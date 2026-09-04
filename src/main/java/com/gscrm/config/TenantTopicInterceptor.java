package com.gscrm.config;

import com.gscrm.security.AuthenticatedUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;

import java.security.Principal;

/**
 * STOMP kanalının kiracı sınırı.
 *
 * <p>CONNECT kimlik ister; SUBSCRIBE ise hiç denetlenmiyordu. Yayın adresleri
 * {@code /topic/salon.{id}.*} biçiminde olduğu ve adresi istemci seçtiği için,
 * kimliği doğrulanmış herhangi bir kullanıcı başka bir işletmenin kanalına abone
 * olup randevu bildirimlerini — müşteri adı ve hizmet dahil — alabiliyordu.
 * Takvim sayfasındaki {@code salon-id} meta'sı sabit {@code 1} olduğu için bu
 * teorik değil, varsayılan davranıştı: 1 numaralı salonun randevuları her
 * kiracının ekranında bildirim olarak çıkıyordu.
 *
 * <p>Artık abone olunan adresteki salon kimliği, oturumun salonuyla birebir
 * eşleşmek zorunda. Salon kapsamı taşımayan {@code /topic/...} adresleri de
 * reddedilir: kiracıya ait olmayan tek yayın {@code CalendarWebSocketController}
 * içindeki {@code @SendTo("/topic/appointments")} ve onu dinleyen istemci yok.
 */
@Slf4j
public class TenantTopicInterceptor implements ChannelInterceptor {

    private static final String SALON_TOPIC_PREFIX = "/topic/salon.";
    private static final String TOPIC_PREFIX = "/topic/";

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            if (accessor.getUser() == null) {
                throw new IllegalArgumentException("WebSocket CONNECT için kimlik doğrulama gerekli");
            }
            return message;
        }
        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            checkSubscription(accessor.getDestination(), resolveSalonId(accessor.getUser()));
        }
        return message;
    }

    private void checkSubscription(String destination, Long sessionSalonId) {
        if (destination == null || !destination.startsWith(TOPIC_PREFIX)) {
            // Uygulama hedefleri (/app/**) ve kullanıcı kuyrukları bu kuralın dışında.
            return;
        }
        Long topicSalonId = extractSalonId(destination);
        if (topicSalonId == null || !topicSalonId.equals(sessionSalonId)) {
            log.warn("Kiracı dışı abonelik reddedildi: {} (oturum salonu={})", destination, sessionSalonId);
            throw new IllegalArgumentException("Bu kanala abone olma yetkiniz yok");
        }
    }

    /** {@code /topic/salon.12.appointments} → {@code 12}; kapsamsız adreslerde null. */
    private Long extractSalonId(String destination) {
        if (!destination.startsWith(SALON_TOPIC_PREFIX)) {
            return null;
        }
        String rest = destination.substring(SALON_TOPIC_PREFIX.length());
        int dot = rest.indexOf('.');
        String id = dot < 0 ? rest : rest.substring(0, dot);
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Long resolveSalonId(Principal principal) {
        Object candidate = principal instanceof Authentication auth ? auth.getPrincipal() : principal;
        return candidate instanceof AuthenticatedUser user ? user.getSalonId() : null;
    }
}
