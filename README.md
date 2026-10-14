# Patrick Jane Edits

*The Mentalist* dizisinden Patrick Jane fan edit'leri hazırlamak için bir plan ve script.

- **[PLAN.md](PLAN.md)**: "Gülümsemenin Arkası" edit'inin planı. Şarkı önerileri, bölüm bölüm sahne listesi ve ipuçları burada.
- **[jane_edit.txt](jane_edit.txt)**: Edit'in zaman çizelgesi. Hangi klip, hangi saniye, kaç vuruş, hangi efekt.
- **[edit.py](edit.py)**: Zaman çizelgesini okuyup klipleri keser, efekt ve yazı ekler, müzikle birleştirir.

Dizi görüntüleri ve müzik bu repoda yok. Kendi bölüm dosyalarını ve şarkını kullanırsın.

## Gerekenler

- Python 3.10 veya üstü
- [FFmpeg](https://ffmpeg.org/download.html) (`ffmpeg` ve `ffprobe` komutları)

## Kullanım

1. Bölüm dosyalarını `klipler/` klasörüne, şarkıyı `muzik/` klasörüne koy.
2. `jane_edit.txt` dosyasını aç:
   - `bpm` ve `muzik_baslangic` değerlerini şarkına göre ayarla.
   - Her klip satırında dosya adını ve sahnenin başladığı zamanı yaz.
3. Kontrol et (video üretmez, dosyaları ve zamanlamayı gösterir):
   ```
   python3 edit.py jane_edit.txt --kontrol
   ```
4. Hızlı önizleme (yarı çözünürlük):
   ```
   python3 edit.py jane_edit.txt --hizli -o onizleme.mp4
   ```
5. Son hali:
   ```
   python3 edit.py jane_edit.txt -o jane_edit.mp4
   ```

`--kontrol` çıktısındaki **şarkıda** sütunu her klibin şarkının hangi anına denk geldiğini gösterir. Kesimleri müzikle eşleştirirken buna bak.

## Zaman çizelgesi biçimi

```
dosya | başlangıç | süre | efekt | yazı
klipler/s01e01.mkv | 0:12:31 | 4b | dark+fadein | herkes gülümsemesini gördü
```

| Alan | Açıklama |
|---|---|
| dosya | Video dosyası (zaman çizelgesine göre göreli yol) |
| başlangıç | Kaynakta sahnenin başladığı an: `12:31`, `0:12:31.5` veya `751.5` |
| süre | `4b` = 4 vuruş (`bpm` ayarından hesaplanır), `2.5` = 2.5 saniye |
| efekt | Birden fazlasını `+` ile birleştir, efekt yoksa `-` |
| yazı | İsteğe bağlı, alt kısımda gösterilir. Türkçe karakter sorun değil |

### Efektler

| Efekt | Ne yapar |
|---|---|
| `bw` | Siyah-beyaz |
| `sepia` | Eski fotoğraf tonu (geçmiş/flashback için) |
| `dark` | Kontrast artar, renk azalır, kenarlar kararır |
| `slow` | Yarı hızda ağır çekim |
| `zoom` | Yavaşça yakınlaşma |
| `flash` | Beyaz flaşla giriş (vuruşta kesimler için) |
| `fadein` | Siyahtan açılma |
| `fadeout` | Siyaha kararma |

### Ayarlar

| Ayar | Açıklama |
|---|---|
| `muzik` | Şarkı dosyası. Boş bırakılırsa sessiz video çıkar |
| `muzik_baslangic` | Şarkının hangi anından başlanacağı |
| `bpm` | Şarkının tempo değeri. `b` ile yazılan süreler için gerekli |
| `format` | `dikey` (1080x1920), `yatay` (1920x1080), `kare` (1080x1080) |
| `fps` | Kare hızı (varsayılan 30) |
| `font` | Yazılar için font dosyası. Boşsa sistemdeki bir font seçilir |

## Notlar

- Kesim noktaları kareye kümülatif olarak yuvarlanır, bu yüzden uzun edit'lerde bile müzikle senkron kayması olmaz.
- Bir klibin kaynağı istenen süreye yetmezse script uyarır ve son kareyi dondurarak süreyi tamamlar.
- Paylaşırken platformlar telifli müziği sessize alabilir. Uygulamanın kendi müzik kütüphanesinden aynı şarkıyı eklemek genelde daha sorunsuzdur.
