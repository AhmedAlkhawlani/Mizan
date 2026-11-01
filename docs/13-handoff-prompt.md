# 13 — برومبت الجلسة التالية (انسخ هذا الملف كاملاً في محادثة جديدة)

> ملف جاهز للنسخ. لا يحتاج القارئ لأي جلسة سابقة: كل السياق هنا وفي `docs/12-progress.md`.

---

أنت المهندس المسؤول عن مشروع **Mizan — ميزان**، تطبيق أندرويد أصلي لقياس استهلاك البيانات وحمايتها.
المستودع: `/Users/ahmdalkhwlany/AndroidStudioProjects/Mizan` (الحزمة `com.maknoon.mizan`، الفرع `main`).

**قبل أي كود:** اقرأ بالترتيب
`README.md` → `docs/12-progress.md` (سجل ما بُني فعلاً) → `docs/02-android-platform.md` (قيود المنصة)
→ `docs/05-roadmap.md` (المراحل ومعايير القبول) → ثم ملفات المخطط الأخرى عند الحاجة.

## القواعد الملزمة

1. **خطوة = كومت واحد** برسالة Conventional Commits (الخطوات مرقّمة في `docs/05`).
2. **قبل كل كومت:** `./gradlew testDebugUnitTest test lintDebug assembleDebug` ويجب أن ينجح.
   (`test` مضافة لأن `:core:domain` وحدة JVM خالصة ومهمتها `test` لا `testDebugUnitTest`.)
3. لا root، ولا APIs مخفية، ولا صلاحيات غير مذكورة في `docs/02` §8.
4. خصوصية مطلقة: لا تحليلات، لا إعلانات، **ولا إذن `INTERNET`** — التطبيق لا يتصل بأي سيرفر أبداً.
5. العربية وRTL أولاً، وكل نص في `strings.xml` (العربية في `values/` الافتراضية، الإنجليزية في `values-en/`).
6. لا تنتقل لمرحلة قبل تحقق معيار قبولها في `docs/05`.
7. تحقّق على جهاز حقيقي لا على المحاكي وحده: جهاز متصل عبر adb باسم SM-N976V، **Android 12 (API 31)**.

## ما هو منجز (لا تعده)

المراحل 0 و1 و2 مكتملة ومُتحقَّقة على الجهاز — التفاصيل والكومتات في `docs/12-progress.md` §2.
باختصار: خمس وحدات Gradle (`:app`, `:core:domain` JVM نقي، `:core:data`, `:core:platform`,
`:core:designsystem`)، `ByteFormatter` + `TrafficAccountant` + `BudgetPolicy` + `BillingCycleCalculator`،
مصدر `TrafficStats`، خدمة أمامية `specialUse`، إشعار دائم، شاشة رئيسية حيّة، بلاطة إعدادات سريعة،
استئناف بعد الإقلاع، Room (`device_daily` + `device_hourly` + `device_state` بمخطط الإصدار 3 وهجرات
1→2 و2→3)، شاشة سجل بمخططي 7 أيام و24 ساعة، دورة فاتورة بيوم بداية، انقسام خلوي/Wi‑Fi،
وتصدير CSV عبر منتقي النظام.
**الإجمالي: 122 اختبار وحدة، 0 فشل، lint 0 أخطاء.**

## من أين تبدأ (بالترتيب)

المرحلة 3 خطوة بخطوة، كل خطوة كومت كما في `docs/05`:

1. **3.1 تدفّق طلب Usage Access** — `feat(onboarding): request usage access with rationale`.
   `PACKAGE_USAGE_STATS` في المانيفست مع `tools:ignore="ProtectedPermissions"`، وفحص المنح عبر
   `AppOpsManager` (‏`unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, ...)` على API 29+ و`checkOpNoThrow`
   قبلها)، وشرح عربي قبل فتح `Settings.ACTION_USAGE_ACCESS_SETTINGS`، وإعادة الفحص عند العودة
   إلى المقدمة. لا تُجبر المستخدم ولا تُظهر «—» كأنها صفر.
2. **3.2 قراءة استهلاك التطبيقات** — `feat(platform): read per-app usage via NetworkStatsManager`.
   `queryDetailsForUid` للـUID التطبيقي (خلوي/Wi‑Fi) مع `NetworkStats.Bucket`؛ البيانات متأخرة
   دقائق فاعرض «حتى آخر مزامنة». تحقّق أولاً على الجهاز أن الاستدعاء يعمل بعد منح الصلاحية يدوياً.
3. **3.3 مزامنة دورية** — `feat(worker): sync per-app usage` (WorkManager كل 15 دقيقة + عند فتح
   التطبيق)، مع جدول `app_daily` كما في docs/03.
4. **3.4 شاشة التطبيقات** — `feat(ui): add apps usage screen` بدل `SectionPlaceholder` في
   `MizanApp.kt`، مع اختيار الفترة وترتيب بحسب الاستهلاك.
5. **3.5 تفصيل تطبيق** — `feat(ui): add per-app detail chart` (7 أيام).
6. **بعدها فقط** تُقاس دقة ≤1% مقابل `NetworkStatsManager` (معيار docs/05 للمرحلة 2) — لا يمكن
   قياسها قبل 3.1–3.2.

## مزالق لا تكتشفها الاختبارات

- **`:core:domain` يُختبر على JDK حديث ويعمل على Android API 26.** استخدم فقط ما هو متاح على API 26:
  `Instant.atZone` نعم، `LocalDate.ofInstant` **لا** (API 34) — تسبّبت مرة بانهيار الخدمة مع نجاح
  كل الاختبارات. تحقّق من أي استدعاء `java.time` جديد.
- **الإصدارات مثبَّتة لسبب:** Compose BOM `2025.10.01` وNavigation `2.9.8`، لأن خط 2026 يطلب
  `compileSdk 37` وAGP 8.13.2 يقف عند 36. لا ترفعها قبل التأكد من توافق `compileSdk`.
- **أي `startForeground`** يجب أن يمرّ عبر `ServiceCompat` وأن يمرّر النوع فقط على API 34+.
- **`git push` يفشل** (`could not read Username`) — لا بيانات اعتماد. لا تفترض أن الكومتات مرفوعة.
- **قاعدة البيانات بوضع WAL**: سحب `mizan.db` وحده لا يعطي آخر البيانات؛ اسحب `-wal` و`-shm` معه،
  أو أوقف العملية قبل السحب حتى تكون الملفات ساكنة.
- **قراءات الواجهات محميّة بـAPI 31+**: `TrafficStats.getRxBytes(String)` غير متاح على `minSdk 26`،
  وlint يُفشل البوابة إن نُسي الحارس. تحته يبقى انقسام Wi‑Fi مجهولاً (`null`) لا صفراً.
- **لوحة التصميم** في `design/concept-board.png` هي المرجع البصري (سرعات سماوية/وردية، شريط ميزانية،
  حلقة ميزانية، تنبيه كهرماني 80%). الفجوة الحالية بينها وبين المنفَّذ موثّقة في
  `docs/12-progress.md` §11 — اجعلها مرجعك عند بناء الشاشات القادمة.

## في كل رد قدّم

ما أنجزته · الملفات · نتيجة البناء/الاختبارات · رقم الكومت · وما لم تتحقق منه بصراحة.
