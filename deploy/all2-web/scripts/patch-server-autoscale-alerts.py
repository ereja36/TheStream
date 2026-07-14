#!/usr/bin/env python3
"""Patch all2 server.js: real CPU %, single-worker Telegram alerts, shared cooldown."""
from __future__ import annotations

import pathlib
import re
import sys

SERVER = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "/root/all/all2/server.js")
text = SERVER.read_text(encoding="utf-8")
orig = text

CPU_HELPER = '''
let lastCpuSample = { idle: 0, total: 0, ts: 0 };
function sampleLinuxCpuPercent() {
  try {
    const raw = fs.readFileSync('/proc/stat', 'utf8');
    const line = String(raw.split('\\n').find((row) => String(row || '').startsWith('cpu ')) || '').trim();
    if (!line) throw new Error('missing cpu line');
    const parts = line.split(/\\s+/).slice(1).map((v) => parseInt(v, 10)).filter((n) => Number.isFinite(n));
    if (parts.length < 4) throw new Error('bad cpu counters');
    const idle = parts[3] + (parts[4] || 0);
    const total = parts.reduce((sum, n) => sum + n, 0);
    const prev = lastCpuSample;
    lastCpuSample = { idle, total, ts: Date.now() };
    if (!prev.total || prev.total <= 0) return 0;
    const dTotal = total - prev.total;
    const dIdle = idle - prev.idle;
    if (dTotal <= 0) return 0;
    return Math.min(100, Math.max(0, (1 - (dIdle / dTotal)) * 100));
  } catch {
    const cpus = Math.max(1, os.cpus()?.length || 1);
    return Math.min(100, (Number(os.loadavg()?.[0] || 0) / cpus) * 100);
  }
}
function isPrimaryAutoscaleAlerter() {
  const instance = String(process.env.NODE_APP_INSTANCE ?? '').trim();
  if (instance === '0') return true;
  if (instance === '') return true; // fork / single process
  return false;
}
function claimSharedAutoscalingAlert(cooldownMs) {
  const lockPath = path.join(DATA_DIR, 'autoscale_alert_lock.json');
  const now = Date.now();
  try {
    const raw = fs.readFileSync(lockPath, 'utf8');
    const prev = Number(JSON.parse(raw)?.at || 0);
    if (Number.isFinite(prev) && (now - prev) < cooldownMs) return false;
  } catch {}
  try {
    fs.writeFileSync(lockPath, JSON.stringify({ at: now, pid: process.pid }), 'utf8');
    return true;
  } catch {
    return false;
  }
}
'''

OLD_TICK = '''async function runAutoscalingAlertsTick() {
  const settings = getPlaybackProtectionSettings();
  const now = Date.now();
  const elapsedSec = Math.max(1, (now - Number(runtimePressureState.updatedAt || now - 15000)) / 1000);
  const mbps = (proxyBytesSinceAutoscaleCheck * 8) / (elapsedSec * 1000 * 1000);
  proxyBytesSinceAutoscaleCheck = 0;
  const cpus = Math.max(1, os.cpus()?.length || 1);
  const load1 = Number(os.loadavg()?.[0] || 0);
  const cpuPercent = Math.min(100, (load1 / cpus) * 100);
  const memoryPercent = getSystemMemorySnapshot().percent;
  const interfaceRates = sampleInterfaceNetworkRates(now);
  const overloaded = cpuPercent >= settings.autoscaling_cpu_percent_threshold
    || memoryPercent >= settings.autoscaling_memory_percent_threshold
    || mbps >= settings.autoscaling_bandwidth_mbps_threshold;
  runtimePressureState = {
    overloaded,
    cpuPercent: Math.round(cpuPercent * 10) / 10,
    memoryPercent: Math.round(memoryPercent * 10) / 10,
    bandwidthMbps: Math.round(mbps * 10) / 10,
    interfaceIngressMbps: interfaceRates.ingressMbps,
    interfaceEgressMbps: interfaceRates.egressMbps,
    updatedAt: now
  };
  if (!settings.autoscaling_alerts_enabled || !overloaded) return;
  const cooldownMs = Math.max(30, Number(settings.autoscaling_alert_cooldown_sec || 300)) * 1000;
  if ((now - lastAutoscalingAlertAt) < cooldownMs) return;
  lastAutoscalingAlertAt = now;
  const nodeMemory = getNodeProcessMemorySnapshot();
  const message = `AUTOSCALE ALERT: CPU ${runtimePressureState.cpuPercent}% | RAM ${runtimePressureState.memoryPercent}% | NodeRSS ${nodeMemory.rssMb}MB | BW ${runtimePressureState.bandwidthMbps} Mbps`;
  await Promise.allSettled([
    sendTelegramAlertMessage(message),
    sendWebhookAlert({
      type: 'autoscale_pressure',
      cpu_percent: runtimePressureState.cpuPercent,
      memory_percent: runtimePressureState.memoryPercent,
      node_rss_mb: nodeMemory.rssMb,
      bandwidth_mbps: runtimePressureState.bandwidthMbps,
      overloaded: true,
      timestamp: now
    })
  ]);
}'''

NEW_TICK = '''async function runAutoscalingAlertsTick() {
  const settings = getPlaybackProtectionSettings();
  const now = Date.now();
  const elapsedSec = Math.max(1, (now - Number(runtimePressureState.updatedAt || now - 15000)) / 1000);
  const mbps = (proxyBytesSinceAutoscaleCheck * 8) / (elapsedSec * 1000 * 1000);
  proxyBytesSinceAutoscaleCheck = 0;
  const cpuPercent = sampleLinuxCpuPercent();
  const memoryPercent = getSystemMemorySnapshot().percent;
  const interfaceRates = sampleInterfaceNetworkRates(now);
  const overloaded = cpuPercent >= settings.autoscaling_cpu_percent_threshold
    || memoryPercent >= settings.autoscaling_memory_percent_threshold
    || mbps >= settings.autoscaling_bandwidth_mbps_threshold;
  runtimePressureState = {
    overloaded,
    cpuPercent: Math.round(cpuPercent * 10) / 10,
    memoryPercent: Math.round(memoryPercent * 10) / 10,
    bandwidthMbps: Math.round(mbps * 10) / 10,
    interfaceIngressMbps: interfaceRates.ingressMbps,
    interfaceEgressMbps: interfaceRates.egressMbps,
    updatedAt: now
  };
  if (!settings.autoscaling_alerts_enabled || !overloaded) return;
  // Only one cluster worker sends Telegram/webhook alerts.
  if (!isPrimaryAutoscaleAlerter()) return;
  const cooldownMs = Math.max(30, Number(settings.autoscaling_alert_cooldown_sec || 300)) * 1000;
  if ((now - lastAutoscalingAlertAt) < cooldownMs) return;
  if (!claimSharedAutoscalingAlert(cooldownMs)) return;
  lastAutoscalingAlertAt = now;
  const nodeMemory = getNodeProcessMemorySnapshot();
  const message = `AUTOSCALE ALERT: CPU ${runtimePressureState.cpuPercent}% | RAM ${runtimePressureState.memoryPercent}% | NodeRSS ${nodeMemory.rssMb}MB | BW ${runtimePressureState.bandwidthMbps} Mbps`;
  await Promise.allSettled([
    sendTelegramAlertMessage(message),
    sendWebhookAlert({
      type: 'autoscale_pressure',
      cpu_percent: runtimePressureState.cpuPercent,
      memory_percent: runtimePressureState.memoryPercent,
      node_rss_mb: nodeMemory.rssMb,
      bandwidth_mbps: runtimePressureState.bandwidthMbps,
      overloaded: true,
      timestamp: now
    })
  ]);
}'''

if "function sampleLinuxCpuPercent()" not in text:
    if "function getServerNetworkSnapshot()" not in text:
        raise SystemExit("anchor getServerNetworkSnapshot missing")
    text = text.replace(
        "function getServerNetworkSnapshot()",
        CPU_HELPER.strip() + "\nfunction getServerNetworkSnapshot()",
        1,
    )

if OLD_TICK not in text:
    if "sampleLinuxCpuPercent()" in text and "isPrimaryAutoscaleAlerter()" in text:
        print("already patched")
        sys.exit(0)
    raise SystemExit("runAutoscalingAlertsTick block not found (or already changed)")

text = text.replace(OLD_TICK, NEW_TICK, 1)

if text == orig:
    raise SystemExit("no changes made")

SERVER.write_text(text, encoding="utf-8")
print(f"patched {SERVER}")
