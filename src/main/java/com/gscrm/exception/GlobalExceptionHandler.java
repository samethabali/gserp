package com.gscrm.exception;

import com.gscrm.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final boolean prodProfile;

    public GlobalExceptionHandler(Environment env) {
        this.prodProfile = Arrays.asList(env.getActiveProfiles()).contains("prod");
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(ConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotAvailableException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotAvailable(ResourceNotAvailableException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((a, b) -> a + ", " + b)
                .orElse("Doğrulama hatası");
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(message));
    }

    /**
     * Hatalı istemci istekleri 400 döndürmelidir.
     *
     * <p>Bunlar ele alınmadığında genel {@code Exception} işleyicisine düşüyor ve
     * istemci hatası 500 olarak raporlanıyordu: hem yanıltıcı, hem de hata
     * kayıtlarını gerçek sunucu arızalarıyla karıştıran bir durum.
     */
    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        log.debug("Hatalı istek: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("Geçersiz istek: gerekli parametreler eksik veya hatalı"));
    }

    /**
     * Bilinmeyen bir adrese yapılan istek 404 döndürür.
     *
     * <p>Ele alınmadığında genel {@code Exception} işleyicisine düşüyor ve var olmayan
     * bir uç "sunucu hatası" gibi raporlanıyordu; hata kayıtlarını da gereksiz yere
     * kirletiyordu.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Böyle bir adres yok"));
    }

    /**
     * Randevu çakışması veritabanı kısıtına takıldığında 409 döndürür.
     *
     * <p>Müsaitlik kontrolü ile kaydın yazılması arasında kilit yok; eşzamanlı iki
     * istek kontrolü birlikte geçebiliyor. Son savunma hattı olan
     * {@code excl_appointment_staff_overlap} kısıtı (V29) ikinciyi reddeder ve
     * kullanıcı burada, alışılmış çakışma mesajını görür.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(DataIntegrityViolationException ex) {
        String detail = ex.getMostSpecificCause().getMessage();
        if (detail != null && detail.contains("excl_appointment_staff_overlap")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Bu uzman seçilen saatte müsait değil"));
        }
        log.warn("Veri bütünlüğü ihlali", ex);
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Kayıt mevcut verilerle çakışıyor"));
    }

    /**
     * Kilit bekleme, deadlock ve optimistic locking çakışmaları 409 döndürür.
     *
     * <p>Bunlar sunucu arızası değil, aynı kaydı aynı anda değiştirmeye çalışan iki
     * isteğin sonucu. Kullanıcıya teknik istisna metni yerine tekrar denemesini
     * söyleyen bir mesaj gösterilir.
     */
    @ExceptionHandler(ConcurrencyFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleConcurrency(ConcurrencyFailureException ex) {
        log.warn("Eşzamanlılık çakışması: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Kayıt aynı anda başka bir işlemle değiştirildi. "
                        + "Lütfen sayfayı yenileyip tekrar deneyin."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Bu işlem için yetkiniz yok"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
        log.error("Unhandled exception", ex);
        String message = prodProfile
                ? "Beklenmeyen bir hata oluştu. Lütfen tekrar deneyin."
                : "Beklenmeyen hata: " + ex.getMessage();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(message));
    }
}
