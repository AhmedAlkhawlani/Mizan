# 06 — الاختبار والتحقق

## هرم الاختبار

```text
        ┌───────────────────┐
        │  Manual / Device  │  قليل، يدوي
        ├───────────────────┤
        │  Compose UI tests │  شاشات حرجة
        ├───────────────────┤
        │  Robolectric      │  سلوك أندرويد بدون جهاز
        ├───────────────────┤
        │  Unit (JVM)       │  الأغلبية: المنطق الخالص
        └───────────────────┘
```

## ما يُختبر بالضرورة

| المكوّن | الاختبارات |
|---|---|
| `ByteFormatter` | حدود 999/1000، KB/MB/GB، صفر، أرقام كبيرة |
| `TrafficAccountant` | يوم جديد، إعادة تشغيل، فجوة، عداد ينقص، partial |
| `BudgetPolicy` | 79/80/100، مرة واحدة لكل مستوى، إعادة تعيين الدورة |
| `CycleCalculator` | يوم بداية 1/15/28، شهر قصير، تغيير المنطقة الزمنية |
| `AnomalyDetector` | وسيط مقاوم للقفزة، cooldown، حد أدنى |
| `AppUsageAggregator` | دمج UID، تطبيقات محذوفة، أسماء مكررة |
| `NetworkProfileResolver` | مطابقة ملف التعريف، metered/unmetered |
| Room DAO | إدراج/تحديث/استعلامات (Room in-memory) |
| DataStore | قراءة/كتابة/قيم افتراضية |

## الاختبارية (Testability)

- لا `System.currentTimeMillis()` في `domain`؛ استخدم `TimeProvider`:
```kotlin
fun interface TimeProvider { fun nowMillis(): Long }
```
- لا `Context` في `domain`.
- كل مصدر بيانات له واجهة + Fake:
```kotlin
interface TrafficSource {
    fun snapshot(): TrafficSnapshot
}
class FakeTrafficSource(...) : TrafficSource
```
- استخدم `kotlinx-coroutines-test` و`runTest`.
- استخدم `Turbine` لاختبار `Flow`.

## اختبارات دقة الأرقام

1. سجّل قيمة `TrafficStats` و`NetworkStatsManager` في نفس اللحظة.
2. قارن إجمالي اليوم بعد 5 دقائق.
3. الفرق المقبول ≤ 1%، وإلا وثّق السبب (تأخر Bucket).
4. اختبر على:
   - Wi‑Fi فقط.
   - خلوي فقط (أو محاكي ببيانات).
   - تبديل Wi‑Fi ↔ خلوي.
   - إعادة تشغيل الجهاز.
   - منتصف الليل (غيّر ساعة الجهاز/استخدم fake clock).

## اختبارات Compose

- `createComposeRule()`.
- اختبر: عرض السرعة، شارة «مقنّن»، زر الإيقاف، ظهور التنبيه، RTL.
- `contentDescription` موجود لكل عنصر تفاعلي.

## الأداء

- **Macrobenchmark** لفتح التطبيق والتنقل.
- **Baseline Profile** لتحسين الإقلاع.
- قياس البطارية: `adb shell dumpsys batterystats` قبل/بعد ساعة.
- ميزانية الأداء: فتح < 400ms، إطار < 16ms، APK < 6MB.

## CI

```yaml
name: Android CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
      - uses: gradle/actions/setup-gradle@v4
      - run: ./gradlew testDebugUnitTest lintDebug assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: mizan-debug-apk
          path: app/build/outputs/apk/debug/*.apk
```

> إن لم يكن `gradlew` موجوداً بعد، أنشئه بـ `gradle wrapper --gradle-version 8.9` أو من Android Studio.

## اختبار يدوي قبل كل إصدار

- [ ] تثبيت نظيف على جهاز حقيقي.
- [ ] منح Usage Access ثم سحبها.
- [ ] إشعار دائم يبقى بعد إعادة التشغيل.
- [ ] تنبيه 80% يظهر بصوت ويبقى حتى «حسناً».
- [ ] تبديل Wi‑Fi/خلوي يحدّث الشارة.
- [ ] RTL كامل بالعربية، وTalkBack يقرأ البطاقات.
- [ ] لا طلبات شبكة خارجية (مراقبة بـ Charles/Proxyman أو `tcpdump`).
