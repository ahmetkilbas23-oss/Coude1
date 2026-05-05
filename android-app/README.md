# 1DM Otomatik İndirici (Android APK)

Bu uygulama, 1DM (1 Download Manager) içinden manuel olarak yapılan film indirme akışını otomatikleştirir. Bir Android Erişilebilirlik Hizmeti olarak çalışır; 1DM'nin iç tarayıcısında play, indir, çözünürlük seçimi ve "Başla" butonlarına sırayla otomatik dokunarak filmleri telefona indirir.

## Nasıl Çalışır

1. Kullanıcı uygulamaya bir kategori URL'si ekler (örn. `https://www.fullhdfilmizlesene.life/filmizle/animasyon-filmleri`).
2. Uygulama bu sayfayı HTTP üzerinden çeker, Jsoup ile film sayfası bağlantılarını çıkarır.
3. Daha önce indirilmemiş tüm filmler kuyruğa eklenir.
4. Erişilebilirlik Hizmeti her sıradaki filmde şu adımları otomatik yapar:
   - Filmi 1DM'ye `Intent.ACTION_VIEW` ile gönderir; 1DM kendi tarayıcısında açar.
   - Kalibre edilmiş koordinatla play (▶) butonuna dokunur.
   - Sağ üstteki indirme FAB'ının dolmasını bekler ve dokunur.
   - Açılan listede tercih edilen çözünürlük + dil (varsayılan **1280×720 / Türkçe**) satırını bulur ve dokunur.
   - Açılan ekranda "Başla" / "Start" butonuna dokunur.
5. WorkManager günde bir kez tüm kategorileri yeniden tarar; sadece yeni eklenen filmleri indirir, daha önce indirilenlere dokunmaz.

## Kurulum

### Gereksinimler
- Android Studio Hedgehog veya üstü
- Android 8.0 (SDK 26) ve üzeri telefon
- Telefonda 1DM kurulu (`com.dv.adm` ücretsiz veya `com.dv.adm.pay` Plus sürümü)

### Derleme
```
cd android-app
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### İlk Çalıştırma
1. Uygulamayı aç → **Ayarlar** sekmesi → **Erişilebilirlik Hizmetini Aç** → "1DM Otomatik İndirme"yi etkinleştir.
2. Ayarlardan **Bildirim Erişimini Aç** → uygulamayı işaretle (1DM indirme başlangıçlarını yakalamak için).
3. **Kalibrasyon** sekmesi → 1DM'i ayrıca açıp bir film sayfasındaki play butonunun konumunu gör; ardından uygulamada o noktaya dokun (yalnızca bir kez yapılır).
4. **Ayarlar**'da çözünürlük (varsayılan `1280`) ve dil (varsayılan `Türkçe`) tercihlerini doğrula.
5. **Kategoriler** → ➕ → kategori URL'sini yapıştır, isim ver, **Ekle**. Örneğin:
   - `https://www.fullhdfilmizlesene.life/filmizle/animasyon-filmleri`
   - `https://www.fullhdfilmizlesene.life/filmizle/komedi-filmleri`
   - `https://www.fullhdfilmizlesene.life/filmizle/aksiyon-filmleri`
6. Filmler **Kuyruk** sekmesinde "Bekliyor" olarak görünür; 1DM otomatik açılarak sırayla indirilmeye başlar.

## Notlar / Sınırlar

- **Kalibrasyon zorunlu**: Play butonu WebView içinde kalır ve Erişilebilirlik API'si onu görmez. Bu yüzden tek seferlik bir koordinat kalibrasyonu gerekir. Telefon yön değişir veya farklı ekran kullanılırsa kalibrasyonu yenileyin.
- **Site DOM değişikliği**: `SiteScraper.kt` içindeki CSS seçicileri kategori sayfasının ilk sayfasından film bağlantılarını çıkartır. fullhdfilmizlesene.life güncellenirse seçici listesini güncelleyin (tek dosya).
- **1DM güncellemesi**: "Başla", "Türkçe" gibi metin eşleşmeleri **Ayarlar**'dan değiştirilebilir tutuldu; arayüz değişirse buradan ince ayar yapın.
- **Yasal**: Araç yalnızca kullanıcının kendi cihazında, kendi 1DM kurulumunu otomatikleştirir. İndirilen içeriklerin telif sorumluluğu kullanıcıya aittir.

## Proje Yapısı

```
android-app/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/oneDmBot/
│       │   ├── db/                     Room: CategoryEntity, FilmEntity, DAOs, Settings
│       │   ├── scrape/SiteScraper.kt   Jsoup ile kategori sayfası kazıma
│       │   ├── service/
│       │   │   ├── MovieAutoDownloadService.kt   Erişilebilirlik orkestratörü
│       │   │   └── OneDmNotifListener.kt         Bildirim dinleyici (geri bildirim)
│       │   ├── work/
│       │   │   ├── CategorySync.kt     Kategori → kuyruk diff
│       │   │   └── DailyCheckWorker.kt WorkManager günlük tarayıcı
│       │   └── ui/
│       │       ├── MainActivity.kt
│       │       └── screens/Categories|Queue|Settings|Calibration Screen.kt
│       └── res/
│           ├── xml/accessibility_service_config.xml
│           └── values, drawable, mipmap-anydpi-v26
├── settings.gradle.kts
└── build.gradle.kts
```
