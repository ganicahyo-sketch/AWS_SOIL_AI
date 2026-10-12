# Catatan keamanan

OpenAI API key **tidak lagi ditanam sebagai default di source code**. Aplikasi mengambil key dari Pengaturan (`ai_api_key`). Key yang sudah tersimpan di perangkat pengguna tetap digunakan.

Untuk distribusi produksi, panggilan OpenAI sebaiknya melalui backend/proxy agar secret tidak tertanam di aplikasi klien. Hindari memasukkan API key ke repository publik.

ThingSpeak Read API Key juga sebaiknya diisi melalui Pengaturan dan tidak dicantumkan kembali di source publik.
