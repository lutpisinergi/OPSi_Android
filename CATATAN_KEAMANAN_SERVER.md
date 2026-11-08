# Catatan keamanan API OPSi sebelum distribusi Android

Pemeriksaan ini berdasarkan `server.js` yang diberikan sebagai sumber. Ini bukan bukti bahwa server live saat ini sama dengan file tersebut; cocokkan file pada server sebelum pemasangan.

## Temuan utama

1. `GET /api/sync-all` memerlukan token, tetapi query mengambil semua baris dari `users`, `laporan`, `areas`, `inventory`, `barang_masuk`, `barang_keluar`, `stock_movements`, `rkb_targets`, `material_requests`, `mr_items`, dan `visit_reports`. Akibatnya, user area yang berhasil login dapat menerima data lintas area melalui respons API walaupun aplikasi hanya menampilkan data area sendiri.
2. `GET /api/users` juga mengembalikan daftar seluruh pengguna kepada token yang valid.
3. Beberapa endpoint tulis seperti `POST /api/reports`, `POST /api/items`, `POST /api/barang-masuk`, `POST /api/barang-keluar`, `POST /api/inventory/adjustments`, `POST /api/mr`, dan `POST /api/rkb-target` perlu diperiksa dan diberi aturan server-side untuk memverifikasi role, area, dan pemilik data; validasi login saja tidak cukup untuk membatasi cakupan area.
4. `GET/POST /api/visit-reports` sudah memiliki pemeriksaan akses laporan kunjungan pada sumber yang diperiksa, dan POST memeriksa area aktif.

## Rekomendasi sebelum digunakan oleh banyak pengguna

- Buat helper otorisasi terpusat yang membaca `req.user` dari JWT dan menentukan apakah akun Head Office atau User Area.
- Pada User Area, ambil area dari token/session/database server, bukan mempercayai `area` atau `area_id` yang dikirim aplikasi.
- Saring respons `/api/sync-all` dan `/api/users` di server sesuai hak akses. Jangan mengirim data lintas area lalu hanya menyembunyikannya di tampilan Android.
- Untuk update/delete, validasi pemilik data/area di query server dan audit seluruh route.
- Setelah perubahan, uji tiap role dengan token terpisah dan pastikan akses lintas area ditolak oleh API, bukan hanya tidak terlihat di UI.

Paket Android ini tidak mengubah `server.js` dan tidak melakukan migrasi database. Temuan ini dicantumkan supaya batas keamanan versi API yang ada tidak disalahartikan sebagai jaminan isolasi data penuh.
