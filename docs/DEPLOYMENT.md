# Udhëzues i plotë: Instalim VPS nga zero

Ky dokument shpjegon hap pas hapi si të vendosësh backend-in e **Shif Shqip** në një VPS të ri.

---

## Hapi 0 — Çfarë të përgatitësh

| Artikull | Shembull |
|----------|----------|
| VPS | Ubuntu 22.04, 1 vCPU, 1–2 GB RAM |
| IP publike | `203.0.113.10` |
| Domain | `api.shifshqip.com` |
| Rekord DNS A | `api.shifshqip.com` → IP e VPS |

> Zëvendëso `api.shifshqip.com` me domainin tënd në të gjitha hapat.

---

## Hapi 1 — Lidhu me VPS

```bash
ssh root@203.0.113.10
```

Përditëso sistemin:

```bash
apt update && apt upgrade -y
```

---

## Hapi 2 — Instalo shërbimet

```bash
apt install -y nginx mariadb-server \
  php8.3-fpm php8.3-mysql php8.3-curl php8.3-mbstring php8.3-xml php8.3-zip php8.3-gd \
  unzip git certbot python3-certbot-nginx ufw
```

Aktivizo shërbimet:

```bash
systemctl enable nginx mariadb php8.3-fpm
systemctl start nginx mariadb php8.3-fpm
```

---

## Hapi 3 — Konfiguro firewall

```bash
ufw allow OpenSSH
ufw allow 'Nginx Full'
ufw enable
```

---

## Hapi 4 — Krijo databazën

```bash
mysql_secure_installation
```

Pastaj krijo databazën dhe përdoruesin:

```bash
mysql -u root -p <<'SQL'
CREATE DATABASE shif_shqip_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'shif_user'@'localhost' IDENTIFIED BY 'FJALEKALIMI_I_FORTE';
GRANT ALL PRIVILEGES ON shif_shqip_db.* TO 'shif_user'@'localhost';
FLUSH PRIVILEGES;
SQL
```

> Ruaj fjalëkalimin — do ta përdorësh në `config.php`.

---

## Hapi 5 — Ngarko dhe importo skemën

Në kompjuterin lokal (ku ke klonuar repozitorin):

```bash
scp -r admin_panel/the_stream root@203.0.113.10:/var/www/shifshqip-api/
scp database/the_stream_db.sql root@203.0.113.10:/tmp/
scp database/migrations/*.sql root@203.0.113.10:/tmp/
```

Në VPS:

```bash
mysql -u shif_user -p shif_shqip_db < /tmp/the_stream_db.sql
mysql -u shif_user -p shif_shqip_db < /tmp/002_license_seed.sql
mysql -u shif_user -p shif_shqip_db < /tmp/001_collation_fix.sql
```

---

## Hapi 6 — Konfiguro panelin PHP

```bash
cd /var/www/shifshqip-api/the_stream/includes
cp config.example.php config.php
nano config.php
```

Vendos:

```php
$host     = 'localhost';
$user     = 'shif_user';
$pass     = 'FJALEKALIMI_I_FORTE';
$database = 'shif_shqip_db';
```

Lejet e dosjeve:

```bash
chown -R www-data:www-data /var/www/shifshqip-api
chmod -R 755 /var/www/shifshqip-api
chmod -R 775 /var/www/shifshqip-api/the_stream/upload
```

---

## Hapi 7 — Konfiguro Nginx

Krijo skedarin e sajtit:

```bash
nano /etc/nginx/sites-available/shifshqip
```

Përmbajtja:

```nginx
server {
    listen 80;
    server_name api.shifshqip.com;

    root /var/www/shifshqip-api/the_stream;
    index index.php;

    client_max_body_size 64M;

    location / {
        try_files $uri $uri/ /index.php?$query_string;
    }

    location ~ \.php$ {
        include snippets/fastcgi-php.conf;
        fastcgi_pass unix:/run/php/php8.3-fpm.sock;
        fastcgi_param SCRIPT_FILENAME $document_root$fastcgi_script_name;
        include fastcgi_params;
    }

    location ~ /\.ht {
        deny all;
    }
}
```

Aktivizo:

```bash
ln -s /etc/nginx/sites-available/shifshqip /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
```

---

## Hapi 8 — SSL me Let's Encrypt

```bash
certbot --nginx -d api.shifshqip.com
```

Certbot konfiguron automatikisht HTTPS.

---

## Hapi 9 — Verifiko instalimin

### Paneli

Hap në shfletues: `https://api.shifshqip.com/`

- Username: `admin`
- Password: `admin`

### API

```bash
curl "https://api.shifshqip.com/api/api.php?get_config&package_name=com.shif.shqip.android&api_key=CHANGE_ME"
```

Duhet të kthejë JSON me `"status":"ok"` (pas gjenerimit të API key nga paneli).

---

## Hapi 10 — Gjenero çelësat për aplikacionin

1. Hyr në panel → **Settings**
2. Kontrollo që `package_name` = `com.shif.shqip.android`
3. Kopjo **Server Key** (vlera e enkriptuar)
4. Shko te **Settings → API Key** ose `api-key.php` → kliko **GENERATE**
5. Kopjo REST API Key

Vendosi në `android/.../Config.java` — shiko [ANDROID-BUILD.md](ANDROID-BUILD.md).

---

## Hapi 11 — (Opsionale) Shërbej APK nga VPS

```bash
mkdir -p /var/www/html/apk
cp app-debug.apk /var/www/html/apk/shif-shqip.apk
```

Shto në Nginx (server block):

```nginx
location /apk/ {
    alias /var/www/html/apk/;
    autoindex on;
}
```

---

## Hapi 12 — Backup i rregullt

```bash
# Backup databaze (crono ditore)
mysqldump -u shif_user -p shif_shqip_db > /backup/shif_$(date +%F).sql

# Backup skedarësh
tar -czf /backup/panel_$(date +%F).tar.gz /var/www/shifshqip-api/the_stream/upload
```

---

## Kontrolli final

| Test | Rezultati i pritur |
|------|-------------------|
| Paneli hapet | Faqja e login-it shfaqet |
| API get_config | JSON `"status":"ok"` |
| Upload imazhi | Funksionon nga paneli |
| Device Playlists | Faqja ngarkohet (jo e bardhë) |

Nëse diçka dështon, shiko [TROUBLESHOOTING.md](TROUBLESHOOTING.md).
