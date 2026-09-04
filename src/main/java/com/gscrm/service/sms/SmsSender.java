package com.gscrm.service.sms;

/**
 * SMS gönderim sağlayıcısı.
 *
 * <p>Bu arayüzün tek amacı, gerçek bir sağlayıcı (ve onun aylık faturası) olmadan
 * doğrulama akışının uçtan uca çalışabilmesi. Bugün tek implementasyon
 * {@link LoggingSmsSender}: kodu log'a basar, hiçbir şey harcamaz.
 *
 * <p>Netgsm/İletiMerkezi gibi bir sağlayıcı eklemek <b>tek bir yeni sınıf</b> demek:
 * {@code @ConditionalOnProperty(name="app.sms.provider", havingValue="netgsm")} ile
 * işaretlenir ve {@code APP_SMS_PROVIDER=netgsm} verilir. Başka hiçbir dosya değişmez.
 * Loglama {@code SmsService}'te olduğu için sağlayıcı sınıfları ona hiç dokunmaz.
 */
public interface SmsSender {

    SmsResult send(String phoneE164, String message, String templateName);

    /** Sağlayıcı adı — sms_log kaydına yazılır. */
    String providerName();

    record SmsResult(boolean success, String providerRef, String errorMessage) {

        public static SmsResult ok(String providerRef) {
            return new SmsResult(true, providerRef, null);
        }

        public static SmsResult failed(String errorMessage) {
            return new SmsResult(false, null, errorMessage);
        }
    }
}
