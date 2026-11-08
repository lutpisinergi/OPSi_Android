# Checklist uji coba OPSi Android

Gunakan akun uji untuk tiap level. Jangan mulai dengan akun produksi untuk pengujian create/delete.

## Instalasi & login
- [ ] Buka project `OPSi_Android` di Android Studio dan Gradle Sync berhasil.
- [ ] Build `debug` berhasil dan APK terpasang pada HP Android 8.0 atau lebih baru.
- [ ] Login menggunakan username dan password akun OPSi yang sudah ada.
- [ ] Login menggunakan NIK dan password.
- [ ] Alamat HTTPS yang benar dapat disimpan.
- [ ] HTTP biasa ditolak.
- [ ] Logout berhasil.
- [ ] Uji sesi kedaluwarsa: aplikasi kembali ke halaman login setelah API mengembalikan 401 atau 403 sesi kedaluwarsa.

## Hak akses
- [ ] Admin/Head Office dapat melihat semua area dan data sesuai API.
- [ ] User Area melihat data sesuai area akunnya pada layar yang menerapkan filter.
- [ ] Head Office semua jabatan yang memenuhi level akses server dapat membuka laporan kunjungan.
- [ ] Area Koordinator dapat membuka laporan kunjungan.
- [ ] Server tetap menjadi sumber keputusan izin; jangan mengandalkan penyembunyian menu saja.

## Laporan kunjungan
- [ ] Filter periode mengambil format YYYY-MM.
- [ ] Periode yang belum valid tidak menampilkan data periode sebelumnya.
- [ ] Pilih area aktif dari dropdown.
- [ ] Foto dari galeri dapat dipilih dan terlihat pada preview.
- [ ] Foto kamera dapat diambil dan terlihat pada preview.
- [ ] Simpan laporan kunjungan, lalu refresh.
- [ ] PDF menampilkan foto.
- [ ] Excel `.xlsx` dapat dibuka di Microsoft Excel dan gambar tampil di kolom Foto Kunjungan.

## Laporan pekerjaan
- [ ] Isi tanggal valid dan uraian pekerjaan.
- [ ] Foto Before, Progress, dan After dapat dipilih.
- [ ] Simpan laporan dan pastikan muncul di daftar.
- [ ] Export PDF dan CSV dapat dibuka.

## Inventory & Asset
- [ ] Filter Inventory/Asset.
- [ ] Catat barang masuk.
- [ ] Catat barang keluar dengan stok mencukupi.
- [ ] Coba barang keluar melebihi stok dan pastikan server menolak.
- [ ] Penyesuaian stok.
- [ ] Riwayat pergerakan stok sesuai dengan transaksi.

## Personil, RKB, dan MR
- [ ] Head Office membuka data personil dan mencari data.
- [ ] Export CSV personil.
- [ ] Edit data personil dengan akun yang berwenang.
- [ ] Hapus hanya data personil uji dan pastikan akun aktif tidak dapat menghapus dirinya sendiri.
- [ ] Simpan target RKB dan periksa setelah sinkronisasi.
- [ ] Ajukan MR dengan lebih dari satu barang.
- [ ] Periksa rincian barang MR.
- [ ] Uji persetujuan MR dengan akun yang sesuai.

## Perangkat mobile
- [ ] Uji modal dengan layar kecil.
- [ ] Pastikan isi modal dapat digulir dan tombol Simpan/Batal tetap dapat dijangkau.
- [ ] Uji orientasi portrait.
- [ ] Uji keyboard saat mengisi form panjang.
- [ ] Uji kamera dan galeri pada perangkat sebenarnya.
