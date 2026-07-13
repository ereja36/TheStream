# Auth / PIN Testing Checklist (Node/Express)

1. Login rate-limit lockout
- Try 6 wrong logins from same IP within 10 min.
- Expected: first 5 attempts processed, subsequent attempt blocked with 429.

2. Parental PIN verify
- Enable parental PIN in Admin Features.
- Set PIN: 0808.
- POST `/k1/api/parental/verify` with 0808 => valid true.
- POST with 1111 => valid false.

3. Locked category behavior
- Add locked category as `Big Brother` (or `custom:big-brother`).
- Open channel in that category.
- Expected: PIN prompt appears once; after success, same browser session should not re-prompt.
