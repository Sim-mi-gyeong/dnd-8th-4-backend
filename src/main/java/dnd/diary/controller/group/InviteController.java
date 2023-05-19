package dnd.diary.controller.group;

import dnd.diary.response.CustomResponseEntity;
import dnd.diary.response.notification.InviteNotificationResponse;
import dnd.diary.service.group.InviteService;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.io.ParseException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("group/invite")
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    @GetMapping("/accept")
    public CustomResponseEntity<InviteNotificationResponse.InviteNotificationInfo> acceptInvite(
            @AuthenticationPrincipal final Long userId,
            @RequestParam Long groupId,
            @RequestParam Long notificationId
    ) throws ParseException {
        return CustomResponseEntity.success(inviteService.acceptInvite(userId, groupId, notificationId));
    }

    @GetMapping("/reject")
    public CustomResponseEntity<InviteNotificationResponse.InviteNotificationInfo> rejectInvite(
            @AuthenticationPrincipal Long userId,
            @RequestParam Long groupId,
            @RequestParam Long notificationId
    ) {
        return CustomResponseEntity.success(inviteService.rejectInvite(userId, groupId, notificationId));
    }
}
