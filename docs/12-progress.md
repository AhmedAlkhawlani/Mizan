# 12 — سجل التقدّم: ما بُني فعلاً حتى الآن

> آخر تحديث: 2026-11-01 · الفرع: `main` · آخر كومت: `5fd02e2`
> المرجع: `docs/05-roadmap.md`. القاعدة المتّبعة: **خطوة = كومت واحد**، وقبل كل كومت تُشغَّل البوابة
> `./gradlew testDebugUnitTest test lintDebug assembleDebug`.

---

## 1) الحالة باختصار

| المرحلة | الحالة |
|---|---|
| 0 — التأسيس | ✅ مكتملة ومُتحقَّقة |
| 1 — السرعة اللحظية | ✅ مكتملة ومُتحقَّقة على جهاز حقيقي |
| 2 — المحاسبة والسجل | 🟡 الخطوة 2.1 فقط (Room + الحفظ)، و2.2 سلوكها مُنفَّذ داخل 2.1 · متبقّي: 2.3–2.5 |
| 3 — استهلاك التطبيقات | ⏳ لم تبدأ |
| 4 — الميزانيات والتنبيهات | ⏳ لم تبدأ |
| 5 — الودجات والتكامل | ⏳ لم تبدأ (البلاطة أُنجزت داخل المرحلة 1) |
| 6 — Data Saver (VpnService) | ⏳ لم تبدأ |
| 7 — الصقل والنشر | ⏳ لم تبدأ |

**الجودة الآن:** 68 اختبار وحدة، 0 فشل · lint: 0 أخطاء (21 تحذيراً كلها «إصدار أحدث متاح» عدا تحذير
AAB لا يخصّ APK) · `assembleDebug` ناجح · التطبيق يعمل بالعربية RTL على جهاز Android 12.

---

## 2) الكومتات (تاريخ المشروع من البداية)

| # | الكومت | الرسالة |
|---|---|---|
| 1 | `a8a5703` | `رفع المشروع` (الحالة الأولية من القالب) |
| 2 | `108e6bf` | `رفع المشروع` |
| 3 | `f41ecd8` | `docs: import architectural blueprints and design specs from desktop` |
| 4 | `a97ca50` | `chore: bootstrap Mizan with Gradle and CI` |
| 5 | `bf5c741` | `feat(core): add decimal byte formatter` |
| 6 | `e26c6b1` | `feat(core): add reboot-safe traffic accountant` |
| 7 | `edce34c` | `feat(core): add budget thresholds policy` |
| 8 | `b581830` | `feat(app): add Compose shell with RTL support` |
| 9 | `ca02ca6` | `feat(platform): add TrafficStats source` |
| 10 | `8a04315` | `feat(service): add monitoring foreground service` |
| 11 | `c3f1430` | `feat(notification): add ongoing speed notification` |
| 12 | `11eb5ed` | `fix(core): use an API 26 date conversion in the accountant` |
| 13 | `6065bf4` | `feat(ui): render live speed on dashboard` |
| 14 | `207eb4d` | `feat(tile): add quick settings tile` |
| 15 | `e29d12c` | `feat(service): persist monitoring preference across boot` |
| 16 | `5fd02e2` | `feat(data): persist device daily usage` |

> ⚠️ **لم يُدفع أي شيء إلى GitHub**: كل الكومتات محلية. `git push origin main` يفشل بـ
> `could not read Username for 'https://github.com'` — لا بيانات اعتماد HTTPS على الجهاز.

---

## 3) المكدس الفعلي (مثبَّت ومُختبر)

| العنصر | الإصدار | ملاحظة |
|---|---|---|
| Gradle | 8.13 | wrapper كما هو |
| AGP | 8.13.2 | سقفه الموصى به compileSdk 36 |
| Kotlin | 2.2.21 | |
| KSP | 2.2.21-2.0.5 | لـ Room |
| compileSdk / targetSdk | 36 / 36 | |
| minSdk | 26 | ADR-003 |
| Bytecode | JVM 17 | |
| Compose BOM | 2025.10.01 | **ليس** آخر إصدار — انظر §7 |
| Navigation Compose | 2.9.8 | |
| Room | 2.7.2 | مخطط مُصدَّر ومُلتزَم |
| DataStore preferences | 1.1.7 | |
| Lifecycle / Activity | 2.10.0 / 1.12.2 | |
| Coroutines | 1.10.2 | |
| اختبارات | JUnit 4.13.2 + Truth 1.4.4 + coroutines-test | لا Robolectric |

---

## 4) الوحدات والحزم

```text
:app                 com.maknoon.mizan            MainActivity · app/ (shell, locale) · ui/dashboard · monitoring · tile · boot
:core:domain  (JVM)  com.maknoon.mizan.core.domain    format · accounting · budget   ← لا import android.*
:core:data           com.maknoon.mizan.core.data      db (Room) · settings (DataStore) · usage (repository)
:core:platform       com.maknoon.mizan.core.platform  traffic (TrafficSource)
:core:designsystem   com.maknoon.mizan.core.designsystem  theme (palette + status colors + MizanTheme)
```

`no :feature:*` — يُفكَّك لاحقاً عند كبر الكود (docs/01).

---

## 5) ما يعمل الآن (سلوك مُتحقَّق عليه)

1. **شاشة رئيسية حيّة**: سرعة تنزيل/رفع (تحديث كل ثانية)، إجمالي اليوم، شارة «يوم جزئي»، سطر حالة
   (تعمل / متوقفة / تعذّر قراءة العدّادات)، وزر بدء/إيقاف واحد.
2. **خدمة أمامية** من نوع `specialUse` تعمل في الخلفية وتبقى بعد الخروج من التطبيق.
3. **إشعار دائم**: `↓ س · ↑ س` وتحته `اليوم 1.15 MB (جزئي)`، قناة LOW صامتة، إجراء «إيقاف مؤقت»،
   والضغط عليه يفتح التطبيق.
4. **بلاطة إعدادات سريعة**: تعرض سرعة التنزيل أثناء فتح اللوحة و«متوقف» عند التوقف.
5. **حفظ التفضيل**: بعد الإقلاع يستأنف المستقبِل المراقبة إن كانت مفعّلة، وعند فتح التطبيق بعد قتل
   العملية تعود المراقبة تلقائياً (من نشاط في المقدمة، المسموح على Android 12+).
6. **حفظ الأرقام**: `device_daily` (صف لكل يوم) + `device_state` (تراكم المحاسب) في `mizan.db`،
   كتابة كل 5 ثوانٍ وفوراً عند تغيّر اليوم. الإجمالي يصمد أمام موت العملية (اختُبر: 66 KB → 276 KB
   استمراراً لا تصفيراً) ويضيف ترافيك ما بعد إعادة التشغيل بدل أن يبدأ من الصفر.
7. **عربية وRTL مفروضة**: اللغة العربية هي موارد `values/` الافتراضية، و`MainActivity` يلفّ السياق
   بالعربية قبل أول إطار، فتظهر الواجهة RTL حتى على جهاز إنجليزي.
8. **خصوصية بالبناء**: لا إذن `INTERNET` إطلاقاً، `allowBackup=false`، ومنع النسخ السحابي
   والنقل بين الأجهزة عبر `dataExtractionRules` + `fullBackupContent`.

---

## 6) الاختبارات (68 اختباراً)

| الملف | العدد | يغطّي |
|---|---|---|
| `ByteFormatterTest` | 17 | حدود 999/1000، التقريب الذي «ينقل» للوحدة التالية (لا `1000 KB`)، السرعات، استقلال الأرقام عن لغة الجهاز |
| `TrafficAccountantTest` | 13 | يوم جديد، إعادة تشغيل، عدّاد يرجع للخلف، فجوة طويلة/قصيرة، ساعة ترجع، partial الملتصق، استمرار الحالة |
| `BudgetPolicyTest` | 14 | 80/100، مرة واحدة لكل مستوى، القفز فوق 100%، إعادة الدورة، حد صفر، نسبة وهامش |
| `TrafficSnapshotsTest` | 6 | `UNSUPPORTED` (−1) ⇒ `null` لا صفر، اشتقاق لحظة الإقلاع |
| `InMemoryUsageStateStoreTest` | 4 | حفظ/قراءة/عزل النسخ |
| `DeviceUsageRepositoryTest` | 9 | أول كتابة، الخنق الزمني، تغيّر اليوم، الاستئناف من الحالة المحفوظة، ترافيك بعد إعادة التشغيل، partial، ساعة ترجع |
| `DashboardUiStateTest` | 5 | «—» عند التعذّر لا صفر، تنسيق الوحدات، تمرير partial |

**لا يوجد** اختبار Room DAO حقيقي (لا Robolectric)، ولا اختبار Compose UI، ولا اختبار أداء.

---

## 7) قرارات وانحرافات عن المخطط (مقصودة ومُوثَّقة)

1. **Compose BOM 2025.10.01 وNavigation 2.9.8** بدل أحدث إصدارات 2026: خط 2026 (`compose 1.12.1`،
   `navigation 2.10.2`) يطلب `compileSdk 37`، وAGP 8.13.2 يقف عند 36 و`android-37` غير مثبّت.
   جرّبتها فعلاً وفشل البناء بـ`checkDebugAarMetadata` قبل النزول.
2. **`minSdk 26`** (القالب كان 28) حسب ADR-003، و**`versionName 0.1.0`** لأننا لسنا 1.0.
3. **لا إذن `INTERNET`** — التطبيق لا يستطيع الوصول للشبكة أصلاً، وهذا أقوى من أي وعد.
4. **العربية في `values/`** لا في `values-ar`، لأن العربية هي اللغة الأولى لا ترجمة (ADR-008)،
   و`values-en/` للإنجليزية.
5. **الإشعار LOW صامت** بدل HIGH: تحديث كل ثانية بصوت تعذيب؛ HIGH محفوظة لتنبيهات الميزانية (المرحلة 4).
6. **مخزن الحالة في الذاكرة داخل المستودع + Room**: واجهة `UsageStateStore` بقيت متزامنة (domain نقي)،
   والكتابة إلى Room مخنوقة زمنياً.
7. **جدول `device_daily` بلا عمودَي خلوي/Wi‑Fi** من مخطط docs/03: لا نقيسهما بعد، والأصفار ستكون كذباً.
   تُضاف في 2.4 مع هجرة مخطط.
8. **تصحيح قاعدتي في كود docs/09 المرجعي**: `BudgetPolicy` هناك كان يطلق «تجاوز» دون تسجيل 80% فيأتي
   تحذير متأخر؛ و`ByteFormatter` كان يطبع `1000 KB` ويستخدم أرقاماً عربية-هندية على لغة عربية.
9. **لا `catch` شامل حول حلقة القياس**: الانهيار الصاخب أفضل من خدمة تعرض «—» وتخفي عللاً.

---

## 8) عيوب حقيقية اكتُشفت وأُصلحت

| العيب | الأثر | الإصلاح |
|---|---|---|
| `LocalDate.ofInstant` (API 34+) في المحاسب | انهيار الخدمة فور أول عيّنة على Android 12، مع نجاح كل اختبارات JVM | `Instant.atZone` (API 26) + توثيق القيد داخل الصنف — كومت `11eb5ed` |
| `startActivityAndCollapse(Intent)` مهجورة | lint يرفضها مع targetSdk 36، وترمي على Android 14 | `startActivity` مباشرة (تُطوي اللوحة تلقائياً) |
| `@StringRes` على معاملات constructor | تحذيرات Kotlin 2.2 | `@param:StringRes` |
| تحذير `DataExtractionRules` | نسخ بيانات محتمل على Android 12+ | `dataExtractionRules` + `fullBackupContent` |

**درس مستفاد:** اختبارات JVM لا تحمي من فجوات API المنصة. أي استدعاء `java.time` أو `android.*`
جديد في `:core:domain` يجب أن يُتحقق من توفّره على API 26 قبل الدفع.

---

## 9) ما لم يُتحقَّق منه (بصراحة)

1. **مسار الإقلاع الحقيقي**: `BootReceiver` مسجّل والصلاحية ممنوحة، لكن `BOOT_COMPLETED` بث محمي
   و`adb` يرفض إرساله؛ يحتاج إعادة تشغيل فعلية للهاتف.
2. **البطارية < 2%/ساعة** (معيار docs/05 للمرحلة 1): يحتاج ساعة قياس بـ`dumpsys batterystats`.
3. **إذن الإشعارات على Android 13+**: الكود يطلبه ولا يعطّل القياس عند الرفض، لكن جهاز الاختبار 12.
4. **دقة ≤1% مقابل `NetworkStatsManager`**: غير ممكنة قبل المرحلة 3 (تحتاج Usage Access). ما تحقّق
   هو الاتساق مع `TrafficStats` (عدّاد الجهاز الذي نقرأه فعلاً).
5. **الدفع إلى GitHub** (§2).

---

## 10) ملاحظة أرقام مهمة: العدّاد يشمل loopback

`TrafficStats.getTotalRxBytes()` يطابق **مجموع كل الواجهات** في `/proc/net/dev`
(1,066.8 MB) لا مجموع ما بعد استثناء `lo` (1,004.4 MB). أي أن «إجمالي الجهاز» عندنا **يتضمّن
traffic الـloopback**. يجب إمّا استثناؤه أو توضيحه في الواجهة عند تنفيذ 2.4 (عدّادات الواجهات).

---

## 11) مجلد التصميم `design/`

**ما حدث له:** نُسخ من سطح المكتب مع `docs/` و`README.md` في الكومت `f41ecd8` كما طُلب، ولم يُعدَّل
ولم يُستخدم كمدخل للتنفيذ حتى الآن (لم تُقرأ اللوحة إلا الآن عند كتابة هذا السجل).

**محتوياته:**
- `concept-board.html` — لوحة 1600×1000 مكتفية بذاتها.
- `concept-board.png` — نسخة مصدَّرة (328 KB)، وتوليدها موثّق في `design/README.md`.
- `design/README.md` — تعليمات إعادة التصدير عبر Chrome headless.

**ما تصوّره اللوحة:** ثلاث شاشات داكنة + شريط جانبي.
- **الرئيسية:** سرعة `12.4 MB/s` سماوية ورفع وردي، مخطط 60 ثانية، شريط «اليوم 1.24 GB / 2.00 GB»،
  زرّا «إيقاف مؤقت» و«الحماية»، شارة «مقنّن» أعلى اليمين.
- **التطبيقات:** حلقة 62% («2 من 6 GB»)، استهلاك اليوم، قائمة أكثر التطبيقات استهلاكاً بأشرطة ملوّنة،
  وتنبيه كهرماني «وصل Chrome من ميزانيته اليومية %80».
- **الحماية:** مفتاح Data Saver، قائمة تطبيقات «محظور/مسموح»، وملاحظة «لا نقتل تطبيقات، بل نمنع
  شبكتها عبر VpnService».
- **الشريط الجانبي:** بلاطة الإعدادات السريعة، الإشعار الدائم (بثلاثة أزرار: إيقاف/فتح/حسناً)،
  ودجت 2×2 بحلقة ميزانية، وبطاقة «Android Reality».

**الفجوة بين اللوحة والمنفَّذ اليوم:**

| عنصر اللوحة | الحالة |
|---|---|
| الهوية (داكن، سماوي/وردي، RTL، 4 تبويبات) | ✅ منفّذ |
| سرعة حيّة + إجمالي اليوم | ✅ منفّذ (بلا مخطط 60 ثانية) |
| شريط ميزانية اليوم وشارة «مقنّن» | ❌ المرحلة 4 |
| مخطط 60 ثانية | ❌ المرحلة 2.3 |
| شاشة التطبيقات وحلقتها | ❌ المرحلة 3 |
| شاشة الحماية | ❌ المرحلة 6 |
| إشعار: يفتقد زرّي «فتح» و«حسناً» ومن/إلى الميزانية | 🟡 جزئي |
| بلاطة الإعدادات السريعة | ✅ منفّذ |
| دجت 2×2 | ❌ المرحلة 5 |
| الوضع الفاتح (غير موجود في اللوحة) | ✅ أضفناه مع تباين 4.5:1 |

---

## 12) أوامر العمل

```bash
# البوابة الكاملة (تشمل اختبارات الوحدة لوحدة JVM لأن اسم مهمتها test لا testDebugUnitTest)
./gradlew testDebugUnitTest test lintDebug assembleDebug

# بناء وتثبيت وتشغيل
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.maknoon.mizan/.MainActivity

# فحص قاعدة البيانات على الجهاز
adb shell run-as com.maknoon.mizan ls databases/
adb shell run-as com.maknoon.mizan cat databases/mizan.db > /tmp/mizan.db
sqlite3 -header -column /tmp/mizan.db "SELECT * FROM device_daily;"
```
