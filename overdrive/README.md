# Overdrive: Quest performans ve overclock uygulaması

Meta Quest 3 (ayrıca 3S, 2 ve Pro) için CPU/GPU overclock, otomatik overclock, oyun profilleri ve performans HUD'u. Gözlükte 2D panel olarak çalışır, APK olarak yüklenir.

## Özellikler

| Bölüm | Ne yapar |
|---|---|
| **Panel** | Canlı FPS, kare süresi, CPU/GPU saat hızı, SoC ve pil sıcaklığı, pil ve RAM. Son 2 dakikanın grafiği, çekirdek başına saat hızları, tek dokunuşla boost. |
| **Overclock** | CPU ve GPU performans seviyesi (Quest 3'te 0–5), yenileme hızı (72/80/90/120 Hz), render çözünürlüğü (0.6×–1.7×), sabit foveated rendering. 5 hazır ön ayar: Eko, Dengeli, Performans, Overdrive, Netlik. |
| **Oto OC** | Otomatik overclock motoru. FPS, CPU/GPU yükü ve sıcaklığı her saniye okur; kare düşünce darboğaz olan birimi yükseltir, boşluk varsa düşürür. Üç mod (Uyarlanabilir, Maks. Sürekli, Eko Akıllı), termal koruma, pil koruması, seviye aralığı ve her kararı nedeniyle gösteren canlı günlük. |
| **Oyunlar** | Oyuna özel profiller. Oyun öne geldiğinde profil kendiliğinden uygulanır; "Başlat" düğmesi ekran ayarlarını oyun açılmadan önce uygular. |
| **Overlay** | Sürüklenebilir yüzen HUD (3 stil, opaklık, boyut, konum, tıklamayı geçirme). Oyun içi istatistikler için Meta'nın OVR Metrics Tool overlay'ini tek tuşla açar/kapatır. |
| **Araçlar** | RAM temizleyici, performans laboratuvarı (oturum kaydı: ortalama FPS, %1 düşük, akıcılık, maks. sıcaklık, pil tüketimi, CSV dışa aktarma), kayıt stüdyosu (kayıt çözünürlüğü, FPS, bit hızı), yakınlık sensörünü kapatma. |
| **Ayarlar** | Shizuku/root kurulumu, tek dokunuşla izin kurulumu, 5 renk teması, ADB betiği dışa aktarma, komut günlüğü. |

Arayüz İngilizce ve Türkçedir; cihaz dili Türkçe ise Türkçe açılır.

## "Overclock" burada ne demek?

Quest'te her oyun CPU ve GPU için bir **performans seviyesi** ister ve çoğu oyun en yükseği istemez. Overdrive bu seviyeleri `debug.oculus.cpuLevel` / `debug.oculus.gpuLevel` gibi çalışma zamanı ayarlarıyla zorlar. SideQuest ve Quest Games Optimizer da aynı yolu kullanır.

- Çipin fabrika üst sınırının **üstüne çıkmaz**. Bu, root ve özel kernel olmadan mümkün değil.
- Yazılıma dokunmaz. **Yeniden başlatma her şeyi stoka döndürür.**
- Gözlük fazla ısınırsa kendi termal korumasıyla yine yavaşlar. Overdrive'ın termal koruması bunu beklemeden seviyeleri düşürür.
- CPU/GPU seviyesi anında etki eder. Çözünürlük, yenileme hızı ve foveation **oyun yeniden açılınca** etki eder.

## Kurulum

### 1. APK'yı indir

APK'yı GitHub Actions derliyor:

- **Releases** sayfasında `overdrive-latest` sürümündeki `Overdrive.apk`, ya da
- **Actions** → *Overdrive APK* → son çalıştırma → *Overdrive-apk* artifact'i.

### 2. Gözlüğe yükle

Gözlükte Geliştirici Modu açık olmalı (telefondaki Meta Horizon uygulaması → Cihaz → Geliştirici modu).

```
adb install -r Overdrive.apk
```

ya da SideQuest'te APK'yı sürükle bırak. Uygulama gözlükte **Kitaplık → Bilinmeyen Kaynaklar** altında görünür.

### 3. Shizuku'yu bağla (bir kez, sonra her yeniden başlatmada)

Performans ayarlarını sadece ADB kabuğu değiştirebilir. Shizuku bu yetkiyi root olmadan uygulamaya verir.

1. [Shizuku](https://github.com/RikkaApps/Shizuku/releases) APK'sını gözlüğe yükle ve bir kez aç.
2. PC'den çalıştır:
   ```
   adb shell sh /storage/emulated/0/Android/data/moe.shizuku.privileged.api/start.sh
   ```
3. Overdrive'ı aç, Shizuku izin isteyince **İzin ver**.
4. **Ayarlar → Tek dokunuşla kurulum**: overlay, bildirim ve arka planda çalışma izinlerini verir.

Gözlük yeniden başlayınca sadece 2. adımı tekrarla. "Bağlanınca geri yükle" açıksa Overdrive profilini kendisi geri uygular.

Root'lu cihazda **Ayarlar → Root kullan** ile Shizuku gerekmez.

### Oyun içi overlay

Horizon OS, normal uygulamaların sürükleyici VR oyunlarının içine çizmesine izin vermez. Yüzen HUD Ana Ekran ve 2D panellerin üstünde görünür. Oyunun içinde istatistik görmek için Meta'nın **OVR Metrics Tool** uygulamasını yükle; Overdrive'daki **Overlay → Oyunda göster** onu açıp kapatır.

## Derleme

```
cd overdrive
./gradlew testReleaseUnitTest assembleRelease
```

JDK 17 ve Android SDK (API 35) gerekir. Çıktı: `app/build/outputs/apk/release/`.

### İmza anahtarı

Release APK, depodaki **herkese açık geliştirme anahtarı** (`keystore/overdrive-dev.jks`) ile imzalanır. Bu sayede her CI derlemesi bir öncekinin üzerine güncelleme olarak kurulur. Uygulamayı satacaksan kendi gizli anahtarını oluştur ve derlemeye ortam değişkenleriyle ver:

```
OVERDRIVE_KEYSTORE=/yol/release.jks
OVERDRIVE_KEYSTORE_PASSWORD=...
OVERDRIVE_KEY_ALIAS=...
OVERDRIVE_KEY_PASSWORD=...
```

Anahtar değişince eski kurulumun kaldırılması gerekir.

## Proje yapısı

```
app/src/main/java/com/overdrive/quest/
  core/model      ayarlar, profiller, cihaz yetenekleri
  core/tweaks     setprop komutları (QuestCommands) ve uygulama (TweakEngine)
  core/monitor    tek kabuk çağrısıyla telemetri, VrApi log ayrıştırıcı
  core/auto       otomatik overclock motoru
  core/session    performans laboratuvarı
  core/shell      Shizuku / root kabuk erişimi
  service         ön plan servisi, oyun profili geçişi, overlay penceresi
  ui              Compose arayüzü
```

Saf mantık (ayrıştırıcılar, otomatik motor, komutlar, oturum istatistikleri) `app/src/test` altında birim testleriyle kontrol edilir.

## Lisanslar

Chakra Petch yazı tipi SIL Open Font License 1.1 ile dağıtılır (`FONT_LICENSE_OFL.txt`). Overdrive, Meta ile bağlantılı değildir.
