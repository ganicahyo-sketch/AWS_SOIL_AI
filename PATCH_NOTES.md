## Identitas aplikasi 1.5.8 — SIM-TANI

- Nama aplikasi pada launcher/daftar aplikasi Android diubah menjadi **SIM-TANI** agar mudah dibedakan dari aplikasi STASIUN CUACA.
- `AndroidManifest.xml` memakai resource `@string/app_name`; nilai resource ditetapkan di `app/src/main/res/values/strings.xml`.
- Nama proyek Gradle, package/applicationId, versi, dan fitur lain tidak diubah pada penyesuaian ini.

AWS 1.5.8 — ESTIMASI PUPUK + RIWAYAT + EFISIENSI PEMULIHAN HARA

Perubahan terisolasi:
- PalmOilDoseModel sekarang memakai asumsi pemulihan hara pupuk berbeda untuk mineral dan gambut, dengan bulk density yang sudah disimpan dari formulir catatan kebun sebagai petunjuk awal (<=0,80 g/cm3 dianggap indikasi gambut; harus dikonfirmasi, bukan klasifikasi pasti).
- Asumsi awal konservatif: mineral N 35%, P 20%, K 50%, Mg 30%; gambut N 25%, P 15%, K 35%, Mg 25%. Ini parameter heuristik yang dapat dikalibrasi kemudian, bukan norma universal/angka resmi PPKS.
- Riwayat pupuk 365 hari dihitung sebagai dosis × grade hara × asumsi pemulihan. Dukungan tambahan inferensi grade label/nama untuk pupuk majemuk 3 angka, urea, SP-36, TSP, KCl/MOP, ZA, kieserite, dan dolomit; verifikasi label dianjurkan. Riwayat selain kg/ha dan tanpa tanggal tidak dikreditkan.
- Kredit hara MgO ikut dihitung jika kadar MgO dicatat atau bisa diperkirakan dari nama produk.
- Laporan menunjukkan kebutuhan hara tersisa, ekuivalen produk/ha, total untuk luas kebun, dan per pokok bila kepadatan tersedia.
- Best practice ditambahkan: pemupukan terbagi, penempatan di zona akar, hindari hujan lebat/genangan, penutup tanah/konservasi, pengelolaan pelepah/residu, drainase dan muka air gambut.
- Tidak menghapus fitur lain; panggilan model lama dipertahankan melalui overload.

Sumber dasar koefisien hara:
- Farrasati et al. (2026), “Quantifying plant nutrient requirements in oil palm”, European Journal of Agronomy, artikel 128054, DOI/landing page: https://www.sciencedirect.com/science/article/pii/S1161030126000730
- Prabowo, Foster & Nelson (2023), “Potassium and magnesium uptake and fertiliser use efficiency by oil palm at contrasting sites in Sumatra, Indonesia”, Nutrient Cycling in Agroecosystems 126:263–278, DOI 10.1007/s10705-023-10289-7.
- Studi 977 kebun sawit rakyat Indonesia menunjukkan input hara sering tidak seimbang dan metode, penempatan serta pembagian aplikasi perlu diperbaiki: https://www.sciencedirect.com/science/article/pii/S0308521X23001348

Batasan: persentase pemulihan yang dipakai dalam model adalah asumsi estimasi konservatif, bukan nilai spesifik yang diambil sebagai angka baku dari publikasi. Data lab/LSU tetap meningkatkan ketepatan. Angka berbasis hasil TBS terutama untuk TM; tidak digunakan sebagai dosis final TBM.


## Open-Meteo: lama penyinaran matahari
- Ditampilkan pada kartu cuaca utama sebagai lama penyinaran matahari dalam jam/hari dari variabel `sunshine_duration` Open-Meteo.
- CSV Open-Meteo historis resolusi per jam menyertakan kolom `sunshine_hours`; CSV harian menyertakan `sunshine_hours` bersama `daylight_hours`. Nilai durasi API dalam detik dikonversi ke jam.
- Definisi Open-Meteo mengikuti durasi sinar matahari saat direct normalized irradiance melewati ambang WMO 120 W/m²; nilainya berbeda dari panjang siang (`daylight_duration`).

## Penyajian hasil analisis — ramah petani
- Catatan Lapangan dan halaman Analisis Agronomi menampilkan hasil dalam kartu terpisah dengan jarak antarbagian dan ukuran teks yang lebih nyaman dibaca.
- Saran tindakan ditampilkan sebelum rincian teknis, kemudian diikuti kondisi tanah, perkiraan kebutuhan pupuk, cuaca/air, peringatan hama dan penyakit, catatan kebun, dan analisis tambahan.
- Label parameter dan satuan diperjelas dengan bahasa Indonesia sehari-hari.
- Untuk kelapa sawit, ringkasan awal serta seluruh rincian data kebun, data laboratorium daun/tanah dan model kebutuhan pupuk tetap dipertahankan pada kartu masing-masing.
- Data mentah `last_field_analysis` dan `last_agronomy_analysis` tetap disimpan seperti sebelumnya agar konteks AI, PDF, riwayat, dan perhitungan yang ada tidak kehilangan isi.
- Jawaban AI diberi format penekanan untuk judul/bagian, tanpa mengubah jawaban asli yang disimpan.
- Perubahan ini fokus pada tampilan; perhitungan, sumber cuaca, OpenAlex, USB soil meter, CSV, riwayat dan fitur khusus sawit tidak sengaja diubah.


## Build fix — GitHub Actions run 38203903945
- A build failure was traced to unresolved dependency `com.github.mik3y:usb-serial-for-android:3.9.0`.
- Added JitPack to `dependencyResolutionManagement.repositories` in `settings.gradle`; this USB serial library is published via JitPack.
- Kept dependency version and USB soil-meter implementation unchanged; no agronomy calculations or existing app features were modified.
- Build must be rerun in GitHub Actions to confirm dependency resolution and expose any subsequent compiler issues.


## Build fix — GitHub Actions run 38206227249 (2026-10-12)
- The latest CI run reached `:app:compileDebugJavaWithJavac` but failed because `CsvDownloadActivity` called `readAll(InputStream)` without defining that helper.
- Added a small UTF-8 stream-reading helper that closes the HTTP response stream after reading it.
- This is an isolated compile fix; CSV export behavior and the remaining app features were left intact.
- Re-run GitHub Actions after uploading this ZIP; the source change has not yet been verified by a successful Android build.
