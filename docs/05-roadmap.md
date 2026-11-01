# 05 — خطة المراحل والكومتات

> قاعدة: **لا ننتقل لمرحلة قبل تحقق معيار قبولها.** كل خطوة = كومت واحد.

## المرحلة 0 — التأسيس (Bootstrap)

**الهدف:** مشروع يبني ويعمل على المحاكي بمنطق خالص مُختبر.

| # | الخطوة | الكومت |
|---|---|---|
| 0.1 | Gradle KTS + Version Catalog + الوحدات | `chore: bootstrap Gradle project with version catalog` |
| 0.2 | CI يبني ويختبر | `ci: add build, test and lint workflow` |
| 0.3 | `ByteFormatter` + اختبارات | `feat(core): add decimal byte formatter` |
| 0.4 | `TrafficAccountant` + اختبارات | `feat(core): add reboot-safe traffic accountant` |
| 0.5 | `BudgetPolicy` + اختبارات | `feat(core): add budget thresholds policy` |
| 0.6 | Room + DAOs + اختبارات | `feat(data): add Room schema and DAOs` |
| 0.7 | DataStore للإعدادات | `feat(data): add settings DataStore` |
| 0.8 | Compose shell + تنقّل + RTL | `feat(app): add Compose shell with RTL support` |

**القبول:** `./gradlew testDebugUnitTest lintDebug assembleDebug` تنجح، >20 اختباراً، التطبيق يفتح بالعربية.

## المرحلة 1 — السرعة اللحظية

| # | الخطوة | الكومت |
|---|---|---|
| 1.1 | `TrafficStatsSource` (واجهة + تنفيذ + fake) | `feat(platform): add TrafficStats source` |
| 1.2 | `MonitoringService` (Foreground + specialUse) | `feat(service): add monitoring foreground service` |
| 1.3 | إشعار دائم حقيقي + إجراء إيقاف | `feat(notification): add ongoing speed notification` |
| 1.4 | `MonitoringViewModel` + الشاشة الرئيسية | `feat(ui): render live speed on dashboard` |
| 1.5 | QS Tile | `feat(tile): add quick settings tile` |
| 1.6 | إيقاف/تشغيل + استمرار بعد الإقلاع | `feat(service): persist monitoring preference across boot` |

**القبول:** السرعة تتحدّث كل ثانية، الإشعار يبقى، الإيقاف يعمل، البطارية < 2%/ساعة شاشة مطفأة.

## المرحلة 2 — المحاسبة والسجل

| # | الخطوة | الكومت |
|---|---|---|
| 2.1 | `DeviceAccountingRepository` (TrafficStats + Room) | `feat(data): persist device daily usage` |
| 2.2 | تصفير منتصف الليل + partial | `feat(core): roll over day and mark partial` |
| 2.3 | رسم 7 أيام + 24 ساعة | `feat(ui): add history charts` |
| 2.4 | دورة الفاتورة (يوم البداية) | `feat(core): support billing cycle start day` |
| 2.5 | تصدير CSV | `feat(export): export usage as CSV` |

**القبول:** إجمالي اليوم يقارب `NetworkStatsManager` بفرق ≤1%، ويصمد بعد إعادة تشغيل الجهاز.

## المرحلة 3 — استهلاك التطبيقات

| # | الخطوة | الكومت |
|---|---|---|
| 3.1 | تدفّق طلب Usage Access | `feat(onboarding): request usage access with rationale` |
| 3.2 | `NetworkStatsSource` (queryDetailsForUid) | `feat(platform): read per-app usage via NetworkStatsManager` |
| 3.3 | Worker المزامنة كل 15 دقيقة + عند الفتح | `feat(worker): sync per-app usage` |
| 3.4 | شاشة التطبيقات + الفترات | `feat(ui): add apps usage screen` |
| 3.5 | تفصيل تطبيق + 7 أيام | `feat(ui): add per-app detail chart` |

**القبول:** تظهر قائمة التطبيقات بأرقام معقولة، وتحديثها لا يستهلك بطارية ملحوظة.

## المرحلة 4 — الميزانيات والتنبيهات

| # | الخطوة | الكومت |
|---|---|---|
| 4.1 | شاشة إعداد ميزانية الجهاز والدورة | `feat(ui): add device budget editor` |
| 4.2 | ميزانية لكل تطبيق | `feat(ui): add per-app budget editor` |
| 4.3 | محرّك التنبيهات 80/100 مرة واحدة | `feat(core): add budget alert engine` |
| 4.4 | إشعار دائم لا يختفي إلا بالضغط | `feat(notification): add persistent budget alerts` |
| 4.5 | كشف الشذوذ (وسيط) | `feat(core): add anomaly detector` |
| 4.6 | ملفات الشبكات + مقنّن/غير مقنّن | `feat(data): add network profiles` |

**القبول:** تنبيه واحد لكل مستوى في الدورة، ويظهر بصوت ويبقى حتى الضغط.

## المرحلة 5 — الودجات والتكامل

| # | الخطوة | الكومت |
|---|---|---|
| 5.1 | Glance Widget 2×1 | `feat(widget): add speed widget` |
| 5.2 | Glance Widget 2×2 (حلقة ميزانية) | `feat(widget): add budget ring widget` |
| 5.3 | إعدادات متقدمة للودجت | `feat(widget): add widget configuration` |
| 5.4 | اختصارات (Shortcuts) | `feat(app): add app shortcuts` |

## المرحلة 6 — Data Saver الحقيقي (VpnService)

| # | الخطوة | الكومت |
|---|---|---|
| 6.1 | شاشة شرح وموافقة VPN | `feat(saver): add vpn consent flow` |
| 6.2 | `MizanVpnService` + TUN + حظر لكل تطبيق | `feat(saver): add per-app vpn firewall` |
| 6.3 | عدّاد حزم/بايتات لكل تطبيق | `feat(saver): count per-app bytes in vpn` |
| 6.4 | استثناء Wi‑Fi فقط | `feat(saver): allow blocked apps on unmetered` |
| 6.5 | حالة الجدار + إحصاءات | `feat(ui): add firewall status screen` |

**القبول:** تطبيق محظور لا يستطيع الوصول للشبكة فعلياً، وبدون root، ومع إمكانية الإيقاف الفوري.

## المرحلة 7 — الصقل والنشر

| # | الخطوة | الكومت |
|---|---|---|
| 7.1 | العربية الكاملة + RTL audit | `feat(i18n): complete Arabic localization` |
| 7.2 | TalkBack + تباين + تقليل الحركة | `feat(a11y): accessibility pass` |
| 7.3 | Onboarding كامل | `feat(onboarding): add first-run flow` |
| 7.4 | التوقيع + Release + F-Droid | `build: add signed release pipeline` |
| 7.5 | التوثيق وصفحة المشروع | `docs: add user guide and privacy policy` |

## ترتيب الأولويات إن ضاق الوقت

1. المرحلة 1 + 2 (قياس وسجل) — قيمة فورية.
2. المرحلة 3 + 4 (تطبيقات وميزانيات) — القيمة الأساسية.
3. المرحلة 6 (VpnService) — التميّز الحقيقي.
4. الباقي.

## مخاطر ومخفّفات

| الخطر | المخفّف |
|---|---|
| Google Play يرفض `PACKAGE_USAGE_STATS` أو VPN | توزيع F-Droid/GitHub، وتبرير واضح، وإتاحة الوظائف الأساسية بدون الصلاحية |
| أرقام NetworkStatsManager متأخرة | عرض وقت آخر مزامنة + وسم partial |
| قتل الخدمة من الشركات المصنّعة | توجيه المستخدم لتعطيل تحسين البطارية + WorkManager احتياطي |
| استهلاك بطارية | فترة تحديث قابلة للضبط + إيقاف عند الشاشة المطفأة (اختياري) |
| VPN واحد فقط | تنبيه واضح إذا كان VPN آخر نشطاً |
