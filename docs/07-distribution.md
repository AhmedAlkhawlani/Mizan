# 07 — التوزيع والسياسات

## خيارات التوزيع

| القناة | مناسب؟ | ملاحظات |
|---|---|---|
| **GitHub Releases + F-Droid** | ✅ موصى به للبداية | حرية كاملة، لا سياسات Play الصارمة |
| **Google Play** | ⚠️ ممكن | `PACKAGE_USAGE_STATS` و`VpnService` يحتاجان تبريراً وسياسة خصوصية |
| **APK مباشر** | ✅ للاستخدام الشخصي | يحتاج تفعيل «تثبيت من مصادر غير معروفة» |

## متطلبات Google Play (إن اخترت Play)

1. **سياسة الخصوصية** منشورة على رابط عام.
2. **Data safety form**: لا جمع، لا مشاركة، حذف محلي.
3. **تبرير الصلاحيات الحساسة:**
   - `PACKAGE_USAGE_STATS`: وظيفة أساسية (عرض استهلاك كل تطبيق).
   - `QUERY_ALL_PACKAGES`: عرض أسماء التطبيقات؛ استخدم `<queries>` إن أمكن.
   - `VpnService`: يجب أن تكون وظيفة أساسية (جدار حماية/قياس)، مع إفصاح واضح.
4. **إشعار الخدمة الأمامية** واضح.
5. **لا إعلانات ولا تحليلات** — يبسّط المراجعة.

## التوقيع والإصدار

```bash
keytool -genkeypair -v \
  -keystore mizan-release.jks \
  -alias mizan \
  -keyalg RSA -keysize 4096 -validity 10000
```

- احفظ `mizan-release.jks` خارج المستودع.
- `keystore.properties` غير متتبَّع.
- `versionCode` يزيد مع كل إصدار، `versionName` دلالي (`1.0.0`).
- وقّع AAB للـ Play وAPK لـ GitHub/F-Droid.

## إصدار F-Droid

- مستودع Git عام + وصف + ترخيص.
- F-Droid يبني من المصدر؛ لا مكتبات مغلقة.
- metadata في `fastlane/metadata/android/en-US/` و`ar/`.

## ما لا نفعله

- لا `READ_SMS`، لا `READ_CONTACTS`، لا `ACCESS_FINE_LOCATION` بلا داعٍ.
- لا جمع IMEI/MAC/SSID.
- لا مكتبات تتبع.
- لا رفع بيانات للسحابة.

## قائمة ما قبل الإصدار

- [ ] `versionCode`/`versionName` محدثان.
- [ ] `lintRelease` بلا أخطاء حرجة.
- [ ] APK/AAB موقّعان.
- [ ] سياسة خصوصية محدثة.
- [ ] بيانات Data safety مطابقة.
- [ ] لقطات شاشة عربية/إنجليزية.
- [ ] اختبار تثبيت نظيف على Android 8 و14+.
