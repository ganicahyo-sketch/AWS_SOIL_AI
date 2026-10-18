# STASIUN CUACA • GANI CAHYO-UNS v1.5.6

Android project lengkap dengan Open-Meteo + Agronomi + Catatan Lapangan.

## Fitur
- Open-Meteo tanpa API key/akun: cuaca saat ini, ET0 FAO, VPD, hujan, radiasi, mata angin.
- Dashboard memisahkan lama siang (matahari terbit–terbenam) dari lama penyinaran matahari (`sunshine_duration`). Hanya `sunshine_duration` yang dipakai dalam analisis agronomi; lama siang hanya informasi tampilan.
- ThingSpeak feed terakhir + ekspor seluruh histori CSV.
- CWT-7in1: N, P, K tersedia; pH; EC uS/cm; kelembapan; suhu tanah.
- Catatan Lapangan terpadu: pengamatan, pupuk, OPT, pengendalian, irigasi, pertumbuhan; umur tanaman dapat diinput sebagai HST/hari, minggu, bulan, atau tahun dan dikonversi ke HST.
- Analisis awal seluruh parameter: pH, N/N-total, P, K, EC/ECe, kelembapan tanah, suhu tanah, OM, CEC/KTK, suhu udara, RH, tekanan, hujan, ET0, angin, UV, visibilitas, Shortwave, PAR energi, PPFD, sunshine, VPD, serta parameter lingkungan lain yang tersedia.
- Analisis pH, N/P/K, EC, kelembapan, VPD, fase tanaman, kebutuhan hara screening, dan risiko OPT per komoditas yang didukung.
- Mode Konvensional/PHT atau Organik.
- Arah angin selalu ditampilkan sebagai mata angin; tidak menampilkan derajat.

## Build lokal
Workflow CI menggunakan Eclipse Temurin JDK 25; source compatibility Android tetap Java 17. Gradle 9.4.1 + Android Gradle Plugin 9.2.0.

## GitHub Actions
File `.github/workflows/build-apk.yml` membuild debug dan release, lalu mengunggah APK sebagai artifact. Jalankan dari tab Actions > Build APK.

## Catatan ilmiah
Mesin agronomi membedakan keputusan deterministik, screening berbasis ambang, dan rekomendasi yang memerlukan kalibrasi lokal. Dosis pupuk tidak boleh dianggap sebagai resep mutlak ketika persamaan STCR spesifik tanah/komoditas tidak tersedia.

## Laporan PDF
Versi ini menyediakan `LAPORAN PDF - SIMPAN / PRINT` dari dashboard. Pengguna dapat memilih laporan ringkas/lengkap dan menyimpan PDF melalui pemilih file Android atau langsung membuka dialog Print Android.
Isi laporan mencakup cuaca Open-Meteo, ET0, VPD, arah angin berbasis mata angin, tanah CWT-7in1, histori catatan/pupuk/OPT, analisis agronomi, rekomendasi teknis, rekomendasi AI terakhir (jika ada), serta catatan dasar ilmiah.


## v1.5.6 agronomi
- Interpretasi pH berbasis profil komoditas dan kebutuhan kapur berbasis pH-buffer/Al-dd/H-dd/CEC/lab.
- P/K memakai kategori berbasis metode; N hanya diklasifikasikan bila ambang metode tersedia.
- EC sensor dipisahkan dari ECe; kelembapan memakai FC/PWP/FTSW bila tersedia.
- Risk screening OPT menyebut nama organisme; bukan diagnosis.
- Mode konvensional/PHT atau organik.
- Laporan PDF menyertakan data, histori, analisis, rekomendasi, dan sumber.


## Pembaruan CSV Open-Meteo & Analisis AI
- Menu **UNDUH DATA CSV** sekarang memiliki sumber **ThingSpeak** dan **Open-Meteo**.
- Open-Meteo mendukung ekspor historis berdasarkan rentang tanggal pada resolusi **per jam** atau **harian**, termasuk cuaca, hujan, tekanan, angin, radiasi, ET₀, VPD dan parameter tanah yang tersedia. Data diberi penanda `source=OPEN-METEO` dan `data_type=HISTORICAL_REANALYSIS`.
- Tampilan analisis agronomi menyembunyikan uraian metodologi/rentang/rumus dari layar utama. Detail ilmiah dipindahkan ke ikon **ⓘ**.
- AI menggunakan ruang output hingga **5000 token** dan diarahkan menghasilkan rekomendasi 0–24 jam, 1–7 hari, air, pemupukan, PHT, prioritas tindakan, serta pencarian web untuk OPT di luar database lokal.
- API key OpenAI tidak lagi ditanam sebagai default di source code. Masukkan key melalui Pengaturan.
