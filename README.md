# OPSi Android Native — PT. SINERGI Integra Services

Proyek aplikasi **Android native** menggunakan Kotlin dan Jetpack Compose. Aplikasi ini berkomunikasi dengan API OPSi yang sudah berjalan. Aplikasi Android tidak membuat database baru dan tidak mengganti `server.js` maupun `sinergi_database.db`.

## Konfigurasi proyek

| Komponen | Konfigurasi |
|---|---|
| Nama aplikasi | OPSi - SINERGI |
| Application ID | `id.co.sinergi.opsi` |
| Bahasa/UI | Kotlin + Jetpack Compose + Material 3 |
| Minimum Android | Android 8.0 / API 26 |
| Compile SDK | API 36 |
| Target SDK | API 35 |
| Server default | `https://opssinergi.my.id/` |
| Login | Username/NIK + password OPSi yang sudah ada |
| Sesi | JWT Bearer token, disimpan pada private app preferences |
| Foto | Kamera atau galeri, dikompresi menjadi JPEG Base64 |

Alamat server dapat disesuaikan pada layar login atau Profil. Alamat server wajib HTTPS.

## Modul yang ada

1. **Login & Profil** — masuk memakai akun OPSi, logout, melihat identitas pengguna, dan mengatur alamat API HTTPS.
2. **Dashboard** — ringkasan jumlah personil, laporan pekerjaan, area aktif, dan item stok sesuai hak akses pengguna.
3. **Laporan Kunjungan** — filter periode bulan (`YYYY-MM`), lihat laporan dan foto, input kunjungan ke area aktif yang tersedia bagi akun sesuai izin server, pilih jenis kegiatan, ambil foto dari kamera/galeri, export PDF dengan foto, dan export Excel `.xlsx` dengan foto tertanam.
4. **Laporan Pekerjaan** — input uraian pekerjaan dengan foto Before, Progress, dan After; daftar laporan; export PDF dan CSV.
5. **Inventory & Asset** — lihat stok, filter Inventory/Asset, tambah master barang untuk Head Office, transaksi barang masuk/keluar, penyesuaian stok, riwayat pergerakan stok, dan export CSV.
6. **Data Personil** — pencarian, export CSV, edit dan hapus untuk Head Office sesuai izin API, serta pembuatan akun oleh Admin.
7. **List Area** — melihat daftar area aktif.
8. **RKB** — melihat target dan realisasi sederhana serta mengatur target melalui API.
9. **Material Request** — mengajukan MR dengan beberapa baris barang, melihat rincian barang pada riwayat, dan persetujuan untuk Head Office.

## Batas fungsi pada versi native ini

Aplikasi native ini adalah versi operasional awal yang terhubung ke backend OPSi, bukan salinan 1:1 seluruh halaman website. Fitur website yang belum seluruhnya tersedia di aplikasi Android:

- CRUD lengkap Master Area (saat ini daftar area tampil baca saja).
- Import Excel Inventory/Asset dan download template import.
- Edit/hapus laporan pekerjaan dari daftar laporan.
- Pengisian dan perubahan kolom Logistik pada Material Request.
- Export PDF/Excel untuk seluruh modul. Saat ini PDF tersedia untuk laporan kunjungan dan laporan pekerjaan; Excel `.xlsx` bergambar tersedia untuk laporan kunjungan; modul data personil, laporan pekerjaan, dan inventory memakai CSV.
- Form MR native belum memiliki seluruh alur multi-tahap persetujuan dan distribusi logistik seperti website.

Daftar ini ditulis agar batas implementasi versi awal jelas dan tidak dianggap sebagai fitur yang sudah lulus uji.

## Persiapan komputer

1. Instal Android Studio versi stabil.
2. Pastikan Android Studio menggunakan JDK 17 (JDK bawaan Android Studio dapat dipilih dari pengaturan Gradle JDK).
3. Pastikan komputer terhubung internet untuk mengunduh Gradle, Android Gradle Plugin, Android SDK Platform 36, dan dependensi.
4. Ekstrak ZIP proyek ke folder lokal, misalnya `C:\OPSi_Android`.

## Membuka proyek di Android Studio

1. Jalankan Android Studio.
2. Pilih **Open**.
3. Pilih folder `OPSi_Android` hasil ekstrak — folder yang berisi `settings.gradle.kts`, bukan folder `app`.
4. Tunggu **Gradle Sync** sampai selesai.
5. Buka **Tools > SDK Manager** dan pastikan **Android SDK Platform 36** terpasang.
6. Sambungkan HP Android dengan USB debugging aktif atau jalankan emulator.
7. Tekan tombol **Run**.

Jika Android Studio meminta Gradle JDK, pilih JDK 17.

## Membuat APK debug

**Windows:** buka folder proyek lalu klik dua kali `BUAT_APK.bat`, atau jalankan dari Command Prompt:

```bat
gradlew.bat assembleDebug
```

**Linux/macOS:**

```bash
chmod +x gradlew BUAT_APK.sh
./BUAT_APK.sh
```

Atau gunakan terminal Android Studio:

```bash
./gradlew assembleDebug
```

APK debug akan berada di:

`app/build/outputs/apk/debug/app-debug.apk`

Untuk distribusi resmi, buat APK bertanda tangan melalui **Build > Generate Signed Bundle / APK > APK**. Simpan keystore dan password di tempat aman; jangan masukkan ke ZIP proyek atau kirim ke orang lain.

## API yang digunakan

Semua permintaan selain login memakai header `Authorization: Bearer <JWT>`.

| Modul | Endpoint backend yang digunakan |
|---|---|
| Login | `POST /api/login` |
| Sinkronisasi | `GET /api/sync-all` |
| Area | `GET /api/areas` |
| Kunjungan | `GET /api/visit-reports?periode=YYYY-MM`, `POST /api/visit-reports` |
| Laporan pekerjaan | `POST /api/reports` |
| Master item | `POST /api/items` |
| Barang masuk/keluar | `POST /api/barang-masuk`, `POST /api/barang-keluar` |
| Penyesuaian stok | `POST /api/inventory/adjustments` |
| Material Request | `POST /api/mr`, `PUT /api/mr/:id/approve` |
| Target RKB | `POST /api/rkb-target` |
| Personil | `POST /api/users`, `PUT /api/users/:id`, `DELETE /api/users/:id` sesuai aturan server |

Akun, password, hak akses, data area, data personil, dan data operasional tetap dikendalikan server OPSi. Aplikasi Android tidak membutuhkan migrasi database untuk fungsi yang disebut di atas.

## Versi toolchain dan kompatibilitas

- Android Gradle Plugin `8.13.2` dengan Gradle Wrapper `8.13`.
- Kotlin `2.3.21`, Compose Compiler Gradle Plugin memakai versi Kotlin yang sama.
- Compose BOM `2026.04.01`, compile SDK 36.

Konfigurasi dipertahankan pada API 36. Jangan menaikkan Compose BOM secara otomatis ke rilis yang mensyaratkan compile SDK/AGP lebih baru tanpa menyelaraskan toolchain proyek.

## Keamanan & operasional

- Server wajib HTTPS; base URL non-HTTPS ditolak oleh aplikasi.
- Token JWT dikirim melalui header Authorization dan dihapus ketika server mengembalikan HTTP 401 atau 403 dengan pesan sesi kedaluwarsa.
- Foto diperkecil dan dikompresi sebelum dikirim. Kualitas/ukuran kompresi dapat disesuaikan di `PhotoUtils.kt`.
- Manifest menonaktifkan backup Android untuk mengurangi risiko data sesi ikut tersalin.
- Jangan mengubah `JWT_SECRET`, database, atau struktur tabel untuk memasang aplikasi native.
- Uji dahulu menggunakan akun percobaan untuk setiap level hak akses sebelum distribusi luas.

### Catatan keamanan backend yang wajib diperhatikan

Pada `server.js` yang menjadi dasar proyek ini, endpoint `GET /api/sync-all` mengambil seluruh data personil, laporan, area, inventory, transaksi, dan Material Request untuk setiap token login yang valid. Aplikasi Android menyaring data yang ditampilkan berdasarkan akun, tetapi penyaringan tampilan bukan pembatasan keamanan API. Selain itu, beberapa endpoint tulis menerima `area`/`area_id` dari payload tanpa validasi cakupan area berdasarkan akun. Sebelum APK dibagikan secara luas, backend perlu diberi otorisasi per endpoint dan filter data di server. Lihat `CATATAN_KEAMANAN_SERVER.md`. Saya sengaja tidak mengubah backend atau database pada paket Android ini.

## Status validasi paket

Sumber Kotlin, konfigurasi Gradle, dan XML diperiksa secara statis; sintaks backend juga dapat diperiksa dengan Node.js. **APK belum dapat dibangun di lingkungan penyusunan ini** karena Android SDK tidak tersedia dan Gradle Wrapper tidak dapat mengunduh distribusi Gradle dari internet pada lingkungan ini. Build APK harus dijalankan pada komputer yang sudah memasang Android Studio/SDK dan memiliki akses internet.
