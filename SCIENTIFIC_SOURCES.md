# Pustaka ilmiah dan sumber teknis — v1.5.6

## Evapotranspirasi, air tanah, dan cuaca
- Allen, R.G. et al. (1998), FAO Irrigation and Drainage Paper 56 (FAO-56): Penman–Monteith reference ET0, crop coefficient Kc, crop ET, TAW/RAW/available soil water.
  https://www.fao.org/land-water/land/land-governance-and-planning/land-resources-planning-toolbox/detail/crop-evapotranspiration-%28crop-et0%29/en
- Open-Meteo documentation: current/daily weather, ET0 FAO, VPD, radiation, sunshine, wind, soil variables, forecast.
  https://open-meteo.com/en/docs

## pH, kebutuhan kapur, dan pembenah
- University of Minnesota Extension: lime requirement needs soil test information beyond pH; buffer pH is used to estimate lime requirement.
  https://extension.umn.edu/ph-and-lime/lime-needs
- Al-Hamdan et al., Journal of Environmental Management 345 (2023) 118531: meta-analysis of soil amendments and crop response.
  DOI: 10.1016/j.jenvman.2023.118531

## N, P, K dan rekomendasi spesifik lokasi
- Kementerian Pertanian RI, Rekomendasi Pupuk N, P, K Tanaman Pangan Edisi 2 (2024): status hara tanah dan rekomendasi spesifik lokasi.
  https://repository.pertanian.go.id/items/69eba091-7cb8-4d9b-8338-1314be912c7c
- Permentan No. 40/2007: rekomendasi P dan K padi sawah spesifik lokasi.
  https://psp.pertanian.go.id/storage/543/Permentan-No.-40-Th.-2007-ttg-Rekomendasi-Pemupukan-N-P-Dan-K-Pada-Padi-Sawah-Spesifik-Lokasi.pdf
- Mehlich-3 method-specific P/K interpretation: the app only applies a category when the selected extraction method provides a usable interpretation; it does not impose a universal category on unknown methods.

## Salinitas / EC
- USDA-NRCS soil salinity guidance: ECe classes are based on saturation-extract electrical conductivity; thresholds used in the engine are <2, 2–<4, 4–<8, 8–<16, and ≥16 dS/m.
  https://directives.nrcs.usda.gov/sites/default/files2/1712930958/20304.pdf
- FAO Water Quality / crop salt tolerance: ECe is the standard root-zone salinity reference and crop tolerance differs by crop.
  https://www.fao.org/4/Y4263E/y4263e0e.htm
- EC from a field sensor is retained as a screening/trend signal and is not automatically converted to ECe.

## VPD dan stres tanaman
- Review physiology literature: VPD around 0.5–1.5 kPa is commonly suitable for many crops, while higher VPD can increase water stress, especially when soil water is limited.
  https://pmc.ncbi.nlm.nih.gov/articles/PMC10422931/

## Prediksi OPT — screening, bukan diagnosis
- Rice blast / panicle blast forecasting: temperature, humidity, rainfall and wetness conditions are used as risk factors.
  https://apsjournals.apsnet.org/doi/10.1094/PHYTO-08-22-0311-R
- Brown planthopper and rice IPM references: weather can inform monitoring, but population and field thresholds remain necessary.
  https://ipm.ucanr.edu/
- Tomato late blight forecasting: cool/wet weather and leaf wetness are important drivers; weather alone does not diagnose disease.
  https://www.apsnet.org/edcenter/pdlessons/Pages/LateBlight.aspx
- Spodoptera frugiperda development / degree-day literature:
  https://pmc.ncbi.nlm.nih.gov/articles/PMC9782183/
- Oil palm pest/disease references used by the engine are treated as crop-specific risk screening and require field confirmation.

## Organic production
- SNI 6729:2016 — Sistem Pertanian Organik Indonesia. Mode organik in the app is intended to respect applicable certification/input requirements; it does not simply rename synthetic inputs as organic.

## Model limitations
- Soil stock conversion used by the app: stock (kg/ha) = concentration (mg/kg) × bulk density (g/cm³) × depth (cm) × 0.10.
- Stock is not equivalent to plant uptake. Uptake depends on availability, root distribution, timing, water, crop demand, and losses.
- Fertilizer requirement is an interpretable STCR-style screening model. It is not a full QUEFTS model and not a locally calibrated STCR/PUTS equation.
- N is classified only when user supplies a valid method-specific lower/upper threshold. P/K are classified only when a method supported by the engine is selected.
- EC sensor is not treated as ECe without a validated conversion for the sensor, soil, and measurement method.
- OPT outputs are risk screening and never a definitive diagnosis.


## Klasifikasi rentang P, K, pH dan EC

- Staf/Pusat Penelitian Tanah (1983): kriteria sifat kimia tanah mencakup lima kelas untuk N, P2O5 HCl, K2O HCl, K-total, dan beberapa sifat lain; pH H2O menggunakan kelas sangat masam, masam, agak masam, netral, agak alkalis, dan alkalis.
- Petunjuk teknis analisis tanah yang dirujuk dalam literatur Indonesia juga menampilkan lima kelas untuk salinitas/DHL: <1, 1–2, 2–3, 3–4, >4 dS/m. Dalam aplikasi, ini dipakai sebagai screening EC sensor dalam µS/cm dengan pembagian 1000 µS/cm = 1 dS/m, bukan sebagai pengganti ECe laboratorium.
- Karena CWT-7in1 menghasilkan nilai sensor yang tidak otomatis identik dengan metode ekstraksi laboratorium, klasifikasi P/K diberi label screening/metode dan tidak diperlakukan sebagai diagnosis laboratorium final.
- Referensi berbasis publikasi: Sufardi et al. (2025), *Soil Quality Index and Water Stress Vulnerability...*, Applied and Environmental Soil Science, yang merangkum kriteria Indonesia untuk Total N, P tersedia, K tertukar, dan pH.


## Analisis PPFD dan seluruh parameter
- Open-Meteo menyediakan shortwave radiation sebagai besaran radiasi gelombang pendek; aplikasi mengubahnya menjadi PAR energi lalu estimasi PPFD melalui helper `LightConversion`.
- Klasifikasi PPFD di aplikasi adalah **screening operasional**, bukan ambang universal untuk semua komoditas. Untuk keputusan fisiologis yang lebih kuat, gunakan kebutuhan foton spesifik komoditas/fase dan, bila memungkinkan, DLI serta pengukuran PPFD lapang yang terkalibrasi.
- Kelembapan tanah juga dianalisis lebih dulu. Nilai sensor dalam % diperlakukan sebagai screening; bila FC dan PWP tersedia, status air dihitung relatif terhadap batas tersebut. Nilai Open-Meteo `soil_moisture_0_to_10cm` dibaca sebagai fraksi volumetrik m³/m³ dan tidak dipaksakan ke kelas universal tanpa FC/PWP.
- Klasifikasi cuaca seperti tekanan absolut, ET0, cahaya dan angin bersifat kontekstual; aplikasi menampilkan status screening dan tetap mengutamakan tren, ketinggian, fase tanaman, dan kondisi lapangan.

## Kelapa sawit — rujukan rekomendasi pemupukan
- Pusat Penelitian Kelapa Sawit (PPKS/IOPRI), layanan **Rekomendasi Pemupukan** untuk TBM dan TM. Deskripsi layanan menyatakan rekomendasi jenis/dosis/waktu aplikasi dilengkapi pengambilan dan analisis sampel tanah dan daun, evaluasi pertumbuhan, kultur teknis, serta saran perbaikan faktor pembatas: https://iopri.co.id/productGroup/detail/rekomendasi-pemupukan
- Catatan implementasi aplikasi: pembacaan NPK dari sensor tanah USB 8-in-1/7-in-1 CWT diperlakukan sebagai skrining lapangan; bukan pengganti analisis laboratorium atau dasar tunggal untuk mengklaim dosis resmi PPKS. EC sensor (µS/cm) tidak otomatis dikonversi menjadi ECe.
- Sensor tanah USB 8-in-1 yang dijual di pasaran dapat melaporkan N/P/K dalam mg/kg, pH, EC, suhu dan kelembapan; spesifikasi dan kalibrasi berbeda menurut model. Contoh dokumentasi teknis: https://www.niubol.com/id/sensor-pertanian/Portable-Type-C-USB-Soil-Multi-Parameter-Tester-15.html
