# 02 — منصة أندرويد: ما يمكن وما لا يمكن

> هذا أهم ملف في المخطط. أي قرار مخالف لما هنا سيُنتج تطبيقاً لا يعمل على الأجهزة الحقيقية.

## 1) قياس السرعة اللحظية — ممكن بدون صلاحيات

| API | ماذا يعطي | ملاحظات |
|---|---|---|
| `TrafficStats.getTotalRxBytes()/getTotalTxBytes()` | إجمالي الجهاز التراكمي منذ الإقلاع | لا يحتاج صلاحية؛ يشمل Wi‑Fi وخلوي |
| `TrafficStats.getMobileRxBytes()/getMobileTxBytes()` | الخلوي فقط | تراكمي |
| `TrafficStats.getRxBytes(iface)` | واجهة محددة (wlan0/rmnet0) | مفيد لفصل Wi‑Fi عن الخلوي |
| `TrafficStats.getUidRxBytes(uid)` | **غير موثوق** لأي UID غير تطبيقك | منذ Android 7 يعيد `UNSUPPORTED` |

**القرار:** السرعة اللحظية تُحسب من فرق العدّادات الكلية على مستوى الجهاز/الواجهة كل ثانية.
لا نحاول نسبة السرعة اللحظية لتطبيق معيّن في v1. `VpnService` في المرحلة 6 يقيس محاولات
المحظور فقط ولا يمرّر ترافيك المسموح (انظر §7).

## 2) استهلاك كل تطبيق — ممكن مع Usage Access

| الخيار | يحتاج | يعطي | حدود |
|---|---|---|---|
| `NetworkStatsManager.queryDetailsForUid(...)` | صلاحية `PACKAGE_USAGE_STATS` عبر إعدادات النظام | بايتات لكل UID/Hash داخل نطاق زمني | بكت (bucket) وليس لحظياً؛ قد يتأخر دقائق |
| `NetworkStatsManager.querySummaryForDevice(...)` | نفس الصلاحية | ملخص الجهاز حسب نوع الشبكة | للتحقق والمقارنة |
| `UsageStatsManager` | نفس الصلاحية | التطبيق في المقدمة | اختياري لتصنيف «خلفية/أمامية» |
| `VpnService` + TUN | موافقة VPN | حظر حقيقي وعدّ محاولات المحظور | VPN نشط واحد فقط؛ أيقونة VPN؛ استهلاك بطارية؛ لا يمرّر ترافيك المسموح بلا `INTERNET` |

**القرار:**
- v1: `NetworkStatsManager` للاستهلاك التاريخي لكل تطبيق، مع مزامنة كل 15 دقيقة وعند فتح التطبيق.
- مرحلة 6: `VpnService` لحظر التطبيقات المحظورة محلياً وعدّ محاولاتها، بلا إذن `INTERNET`.

**كيف تُطلب الصلاحية؟**
```kotlin
val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
```
والتحقق:
```kotlin
val appOps = context.getSystemService(AppOpsManager::class.java)
val mode = appOps.checkOpNoThrow(
    AppOpsManager.OPSTR_GET_USAGE_STATS,
    Process.myUid(),
    context.packageName
)
val granted = mode == AppOpsManager.MODE_ALLOWED
```
وتُعلَن في المانيفست:
```xml
<uses-permission
    android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
```

## 3) الشبكات المقنّنة والهوتسبوت

| السؤال | API | ملاحظة |
|---|---|---|
| هل الشبكة الحالية مقنّنة؟ | `ConnectivityManager.isActiveNetworkMetered` | ✅ الأساس |
| هل المستخدم قال «غير مقنّنة» مؤقتاً؟ | `NET_CAPABILITY_TEMPORARILY_NOT_METERED` (API 30) | ✅ |
| Wi‑Fi أم خلوي؟ | `NetworkCapabilities.TRANSPORT_WIFI/CELLULAR` | ✅ |
| تجوال؟ | `NET_CAPABILITY_NOT_ROAMING` | ✅ |
| اسم الشبكة (SSID)؟ | `WifiInfo` من `NetworkCapabilities` | ⚠️ يحتاج `ACCESS_FINE_LOCATION` على Android 10+ |
| هل أنا نقطة اتصال (Hotspot)؟ | `TetheringManager`/حالة النظام | ⚠️ واجهات محدودة |
| استهلاك الدورة من المشغّل | `NetworkStatsManager` + `SubscriptionManager` | ⚠️ لا توجد واجهة موحّدة موثوقة |

**القرار:** لا نطلب صلاحية الموقع لأجل SSID. بدلاً منها:
- نستخدم `isActiveNetworkMetered` كمصدر أساسي.
- المستخدم ينشئ **ملفات تعريف** (اسم حر): «البيت — غير مقنّن»، «هوتسبوت — مقنّن 2GB»، ويربطها يدوياً بالشبكة الحالية.
- عند تغيّر الشبكة نسأل مرة واحدة: «هل نعتبر هذه الشبكة مقنّنة؟».

## 4) التنبيهات التي «لا تختفي إلا بالضغط»

| القدرة | API | الحقيقة |
|---|---|---|
| إشعار دائم لا يُكنسل بالسحب | `setOngoing(true)` + `setAutoCancel(false)` | ✅ على معظم الأجهزة، مع اختلافات من الشركات |
| صوت عند الظهور | قناة `IMPORTANCE_HIGH` + `setSound` | ✅ |
| إبقاء الشاشة/إظهار ملء الشاشة | `USE_FULL_SCREEN_INTENT` | ❌ مقيّد في Android 14 لتطبيقات المكالمات/المنبّه فقط |
| إشعار يتطلب ضغطاً | `setOngoing(true)` + إجراء «حسناً» | ✅ الأفضل عملياً |
| إعادة إظهاره دورياً | `AlarmManager`/`WorkManager` | ⚠️ Exact alarms مقيّدة على 12+ |

**القرار:** قناة `IMPORTANCE_HIGH` + إشعار `ongoing` بصوت وإجراء «حسناً»، مع إعادة الإظهار عند
كل حدث. لا نعتمد على full-screen intent.

## 5) الخدمة الأمامية (Foreground Service)

- مطلوبة لقياس مستمر؛ بدونها سيقتل النظام العملية.
- Android 14+: يجب تحديد `foregroundServiceType`:
  - `dataSync` ⇒ يتطلب `FOREGROUND_SERVICE_DATA_SYNC`، ومقيّد في الخلفية.
  - `specialUse` ⇒ يتطلب `FOREGROUND_SERVICE_SPECIAL_USE` وتبريراً في Play.
- إشعار الخدمة إلزامي ولا يمكن إخفاؤه.
- **القرار:** `specialUse` مع تبرير واضح، مع مفتاح «إيقاف المراقبة» للمستخدم، وتقليل التحديث للثانية فقط أثناء التشغيل الظاهر.

## 6) العمل في الخلفية

| الأداة | الاستخدام | القيود |
|---|---|---|
| `ForegroundService` | القياس اللحظي | إشعار إلزامي |
| `WorkManager` | مزامنة كل 15 دقيقة | الحد الأدنى 15 دقيقة |
| `AlarmManager` | منتصف الليل/الدورة | Exact يحتاج صلاحية ومقيّد |
| `RECEIVE_BOOT_COMPLETED` | إعادة الجدولة بعد الإقلاع | ✅ |

**القرار:** التصفير اليومي يُفحص عند: فتح التطبيق، بدء الخدمة، واستيقاظ WorkManager. لا نعتمد على منبّه دقيق.

## 7) `VpnService` للحظر الحقيقي (مرحلة 6)

**قرار 2026-11-02: حجب فقط، بلا إذن `INTERNET`.**

- `VpnService.prepare()` → موافقة المستخدم.
- `Builder.addAllowedApplication` للتطبيقات **المحظورة فقط**؛ بقية التطبيقات تبقى خارج النفق.
- TUN يستقبل حزم المحظور فقط وتُسقط فوراً؛ لا يُفتح أي مقبس خارجي ولا يُمرَّر ترافيك.
- الفائدة: يبقى التطبيق بلا إذن `INTERNET`، فلا يستطيع الاتصال بأي سيرفر حتى لو أراد.
- الأثر: عدّاد VPN يغطي **محاولات المحظور** فقط؛ استهلاك المسموح يبقى من `NetworkStatsManager`،
  والسرعة اللحظية لكل تطبيق تبقى خارج النطاق (بخلاف الافتراض الأولي في §1).
- قيود: VPN واحد فقط؛ لا يعمل مع VPN آخر؛ أيقونة في شريط الحالة؛ استهلاك بطارية؛ سياسات Play صارمة.
- **القرار:** مرحلة منفصلة اختيارية، مع شاشة شرح صريحة، وبدون أي إرسال بيانات خارج الجهاز.

**التنفيذ في 6.2 (`MizanVpnService`):**
- الخدمة معلنة بـ`android:permission="android.permission.BIND_VPN_SERVICE"` مع فلتر
  `android.net.VpnService`، و`foregroundServiceType="specialUse"` ونوعه الفرعي
  `blocking_only_vpn_firewall`، وإشعار قناة LOW صامت بإجراء «إيقاف».
- النفق مزدوج المكدّس: عنوان `10.111.222.1/32` و`fd00:6d69:7a61:6e::1/128`، مسار افتراضي
  `0.0.0.0/0` و`::/0`، DNS وهمي `10.111.222.2` و`fd00:6d69:7a61:6e::2`، MTU 1500،
  و`setBlocking(true)`. حلقة قراءة واحدة تقرأ كل حزمة وتُسقطها؛ لا مقبس، ولا NAT، ولا إعادة توجيه.
- المخطط النقي `planVpnTunnel` في `:core:domain` ينقّي القائمة (فارغ، اسم الحزمة الذاتي، غير
  المثبّت) ويوحّد ويرتّب. مخطط `null` أو عدد تطبيقات صفر ⇒ لا `establish()`، لأن Builder بلا
  `addAllowedApplication` يوجّه **كل** تطبيقات الجهاز إلى النفق.
- تعديل القائمة أثناء التشغيل لا يعدّل نفقاً قائماً: الخدمة تعيد البناء داخلياً بجولة جديدة من
  عدّاد `generation` يمنع سباق حلقة قديمة مع نفق جديد. التفضيل محفوظ في DataStore
  (`data_saver_enabled`، `data_saver_blocked_packages`).
- الإيقاف الصريح يصل كأمر `ACTION_STOP` إلى الخدمة التي تغلق `ParcelFileDescriptor` ثم
  `stopSelf`؛ `stopService` وحدها لا تدمّر خدمة VPN نشطة لأن النظام يمسكها. بعد الإغلاق يُحرَّر
  الربط ويُستدعى `onDestroy`.
- قيود معروفة: الحجب بالـUID (تطبيقات تشترك في UID واحد تُحجب معاً). لا صلاحية جديدة على
  المستخدم ولا إذن `INTERNET`.

**التنفيذ في 6.3 (عدّ المحاولات):**
- حلقة القراءة تحسب كل حزمة تُسقط: `parseIpPacket` في `:core:domain` يقرأ رأس IPv4/IPv6
  (البروتوكول، العناوين، منافذ TCP/UDP) ويعيد حجم الحزمة الكامل، و`BlockedTrafficSnapshot.count`
  يجمع الحزم والبايتات لكل حزمة أو لدلو `unknown`.
- الإسناد عبر `ConnectivityManager.getConnectionOwnerUid` (API 29+) المتاح للـVpnService النشط
  لتوصيلات نفقها بلا صلاحية إضافية، بمخبأ تدفّق وخريطة uid→حزمة. أي UID مشترك بين أكثر من حزمة،
  أو حزمة بلا منافذ (ICMP، رؤوس امتداد IPv6)، أو UDP غير متصل، أو جهاز أقدم من 29 تُنسب إلى
  `unknown` لا إلى تطبيق مُخمَّن.
- النشر عبر `SaverTrafficHolder` (StateFlow) كل 500 ms؛ العدّاد يبقى عبر إعادة بناء النفق
  ويُصفَّر عند إنشاء الخدمة، والعرض في شاشة حالة 6.5 (انظر أسفل).

**التنفيذ في 6.4 (السماح على غير المقنّن):**
- قرار الحجب `shouldBlockTraffic(resolved) = resolved.connected && resolved.metered` في
  `:core:domain`، و«مقنّن» يأتي من بنية 4.6: `NetworkProfilesRepository.resolve` على
  `AndroidNetworkStateSource.snapshot` (ملف التعريف ثم راية النظام ثم «غير مقنّن مؤقتاً»).
- قبل تأسيس النفق تقرأ الخدمة القرار؛ إن كان غير مقنّن تبقى منشأةً وتنشر حالة `unmetered`
  وإشعاراً «الشبكة غير مقنّنة» بدل تأسيس نفق، ويبقى `data_saver_enabled = true` ليعود الحجب
  فور العودة إلى مقنّن.
- **متابعة الشبكة بلا حلقة إعادة بناء**: بدل `registerDefaultNetworkCallback` (الذي يرى نفق
  VPN نفسه كشبكة افتراضية ويعيد الاستدعاء)، سُجّل `NetworkRequest` مرشّح بالنقل
  (WIFI|CELLULAR|ETHERNET) مع قرار `@Volatile blocking`، و`onNetworkChanged` يعيد البناء فقط
  عند عبور مقنّن↔غير مقنّن فعلي. `unregisterNetworkCallback` في الإيقاف الصريح و`onRevoke`
  يمنع سباق حدث متأخر يعيد الحجب بعد الإيقاف.

**التنفيذ في 6.5 (شاشة حالة الجدار):**
- `SaverViewModel` يجمع خمسة مصادر في `uiState`: التفعيل، القائمة المحظورة، التطبيقات المثبتة،
  `SaverStateHolder`، وزوج (`ResolvedNetwork?` من `NetworkProfilesRepository.resolve` على لقطة
  شبكة حيّة، و`BlockedTrafficSnapshot` من `SaverTrafficHolder`). اللقطة تُحدَّث عند كل تغيّر في
  `ConnectivityManager` عبر `NetworkRequest` مرشّح بالنقل (مثل 6.4) وعند `onResume`، فلا تعتمد
  الشاشة على قراءة لحظية تتقادم.
- الواجهة تقرأ البيانات ولا تعيد حساب شيء: المجاميع والحزم/البايتات لكل حزمة تُعرض كما يبثّها
  العدّاد، ودلو `unknown` يظهر صراحةً «غير منسوب». لا استدعاء `NetworkStatsManager` هنا، ولا
  صلاحية جديدة.
- الهوية: مخطط داكن ثابت scoped (لا Dynamic Color) يلفّ `Scaffold` كاملاً على هذا التبويب فقط،
  وأيقونات شريط النظام تصبح فاتحة عبر `WindowInsetsController` وتُعاد مع مغادرة التبويب. كل
  النصوص في `values/` و`values-en`، والأرقام تُعرض LTR داخل جملة RTL.

## 8) الصلاحيات المطلوبة (القائمة النهائية لـ v1)

```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"
    tools:ignore="QueryAllPackagesPermission" />
```
- **لا `INTERNET`**: أُزيل بقرار 2026-11-02. الحجب لا يمرّر ترافيكاً ولا يفتح مقبساً خارجياً،
  فلا يحتاجه التطبيق؛ وبقاؤه غائباً هو أقوى ضمانة أن ميزان لا يتصل بأي سيرفر.
- خدمة VPN (`MizanVpnService`، 6.2) تعلن `android:permission="android.permission.BIND_VPN_SERVICE"`،
  وهي صلاحية على مستوى المكوّن لا إذن مستخدم، ولا تُضاف إلى القائمة أعلاه.
- `QUERY_ALL_PACKAGES` لأجل عرض أسماء التطبيقات؛ بديله `<queries>` إن أمكن لتقليل سياسة Play.
- لا `READ_PHONE_STATE`، ولا `ACCESS_FINE_LOCATION` في v1.

**صلاحيتان تظهران في APK ولا تُكتبان هنا، لأنهما مدمجتان من المكتبات لا منّا:**

| الصلاحية | المصدر | الأثر |
|---|---|---|
| `WAKE_LOCK` | `androidx.work:work-runtime` | إبقاء الجهاز مستيقظاً أثناء مزامنة دورية |
| `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | `androidx.core` | صلاحية داخلية للتطبيق نفسه تحمي مستقبلاته |

تُتحقَّق القائمة الفعلية من الـAPK لا من المصدر: `aapt2 dump permissions <apk>`.
آخر تحقق (1.0.2): لا `INTERNET`، والصلاحيات هي السبع أعلاه + هاتان.

## 9) خصوصية ودقة — قواعد صارمة

1. لا نجمع أسماء مواقع ولا DNS ولا محتوى.
2. لا نرسل أي شيء للخارج؛ التطبيق يعمل كلياً offline.
3. عندما نعرض رقماً من `NetworkStatsManager` نقول إنه «حتى آخر مزامنة»، لا «لحظي».
4. عند تعذّر القياس نعرض «—» ولا نخمّن.
5. تُوسم الأيام الجزئية `partial` في قاعدة البيانات والواجهة.

## 10) مصفوفة القدرات باختصار

| الميزة | ممكن؟ | الطريقة | ملاحظة |
|---|---|---|---|
| سرعة لحظية للجهاز | ✅ | TrafficStats | دقة جيدة |
| سرعة لحظية لكل تطبيق | ⚠️ | VpnService فقط | مرحلة 6 |
| استهلاك كل تطبيق | ✅ | NetworkStatsManager | يحتاج Usage Access |
| منع تطبيق من الشبكة | ✅ | VpnService | بدون root |
| قتل تطبيق | ✅ | `ActivityManager.killBackgroundProcesses` | غير موثوق؛ نتجنبه — الحظر أفضل |
| تمييز مقنّن | ✅ | isActiveNetworkMetered | ✅ |
| SSID الشبكة | ⚠️ | WifiInfo + موقع | نتجنبه في v1 |
| إشعار دائم بصوت | ✅ | Ongoing + channel | اختلافات OEM |
| Widget | ✅ | Glance | ✅ |
| QS Tile | ✅ | TileService | ✅ |
