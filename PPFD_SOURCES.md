# Dasar ilmiah estimasi PAR dan PPFD

Aplikasi menggunakan pendekatan broadband dari radiasi Shortwave Open-Meteo:

1. **PAR (W/m²) = 0,45 × Shortwave (W/m²)**. Rasio 0,45 dipakai sebagai pendekatan praktis PAR terhadap radiasi surya global dan dilaporkan dalam literatur PAR. Nilainya dapat berubah menurut kondisi atmosfer dan spektrum.

2. **PPFD (µmol/m²/s) = PAR (W/m²) × 4,57 µmol/J**. Faktor 4,57 µmol/J berasal dari McCree untuk radiasi global sun+sky dan banyak digunakan sebagai pendekatan konversi PAR energi ke fluks foton. Karena W = J/s, satuannya langsung menjadi µmol/m²/s.

**Referensi utama**
- McCree, K. J. (1972). *Test of current definitions of photosynthetically active radiation against leaf photosynthesis data*. Agricultural Meteorology, 10, 443–453. DOI: 10.1016/0002-1571(72)90045-3.
- *Comparison of Satellite, Model, and In Situ Values of Photosynthetically Available Radiation (PAR)*, Journal of Atmospheric and Oceanic Technology, vol. 36 (2019): penggunaan rasio 0,45 untuk mengubah shortwave radiation menjadi PAR pada W/m².
- Foyo-Moreno et al. / review modelling PAR (2022): menegaskan PPFD dalam µmol m⁻² s⁻¹ dan penggunaan rasio McCree 4,57 µmol/J sebagai pendekatan yang umum.

**Catatan penggunaan:** nilai PPFD di aplikasi adalah **estimasi**, bukan pengganti sensor quantum/PAR terkalibrasi. Ketidakpastian berasal dari spektrum matahari, awan, aerosol, sudut matahari, dan rasio PAR/Shortwave yang tidak selalu konstan.
