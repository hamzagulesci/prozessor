// IUserService.aidl
// Shizuku'nun shell (uid=2000) bağlamında çalışan UserService'in AIDL arayüzü.
// Bu arayüz üzerinden MainProcess <-> UserService arasında IPC yapılır.
package com.hamza.prozessor;

interface IUserService {

    // ─── Bilgi okuma ──────────────────────────────────────────────────────────

    /** dumpsys meminfo --oom çıktısını döndürür */
    String getDumpsysMeminfo();

    /** top -n 1 -b çıktısını döndürür (CPU anlık durum) */
    String getTopOutput();

    /** dumpsys alarm çıktısını filtreli döndürür */
    String getAlarmInfo(String packageFilter);

    /** dumpsys batterystats çıktısını döndürür */
    String getBatteryStats(String packageName);

    // ─── Eylemler ─────────────────────────────────────────────────────────────

    /** am force-stop <package> */
    boolean forceStopPackage(String packageName);

    /** cmd appops set <package> RUN_IN_BACKGROUND deny */
    boolean restrictBackground(String packageName);

    /** cmd appops set <package> RUN_IN_BACKGROUND allow */
    boolean allowBackground(String packageName);

    /** pm revoke <package> <permission> */
    boolean revokePermission(String packageName, String permission);

    /** pm grant <package> <permission> */
    boolean grantPermission(String packageName, String permission);

    /** cmd appops set <package> SYSTEM_ALERT_WINDOW deny */
    boolean revokeOverlayPermission(String packageName);

    // ─── Servis kontrolü ──────────────────────────────────────────────────────

    /** Servis versiyon numarasını döndürür */
    int getVersion();

    /** Servisi temiz şekilde kapat */
    void destroy();
}
