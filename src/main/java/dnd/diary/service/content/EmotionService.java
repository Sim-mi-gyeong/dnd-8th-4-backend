package dnd.diary.service.content;

import dnd.diary.domain.content.Content;
import dnd.diary.domain.content.Emotion;
import dnd.diary.domain.user.User;
import dnd.diary.dto.content.EmotionDto;
import dnd.diary.enumeration.Result;
import dnd.diary.exception.CustomException;
import dnd.diary.repository.user.UserRepository;
import dnd.diary.repository.content.ContentRepository;
import dnd.diary.repository.content.EmotionRepository;
import dnd.diary.response.CustomResponseEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmotionService {
    private final ContentRepository contentRepository;
    private final EmotionRepository emotionRepository;
    private final UserRepository userRepository;

    public CustomResponseEntity<EmotionDto.AddEmotionDto> addEmotion(
            UserDetails userDetails, Long contentId, EmotionDto.AddEmotionDto request
    ) {
        validateAddEmotion(request,contentId);

        User user = getUser(userDetails);
        Emotion existsEmotionUser = emotionRepository.findByContentIdAndUserId(contentId, user.getId());

        if (existsEmotionUser == null) {
            return CustomResponseEntity.success(EmotionDto.AddEmotionDto.response(
                            emotionRepository.save(
                                    Emotion.builder()
                                            .emotionStatus(request.getEmotionStatus())
                                            .content(getContent(contentId))
                                            .user(user)
                                            .build()
                            )
                    )
            );
        } else {
            emotionRepository.deleteById(existsEmotionUser.getId());
            return CustomResponseEntity.successDeleteEmotion();
        }
    }

    // method
    private User getUser(UserDetails userDetails) {
        User user = userRepository.findOneWithAuthoritiesByEmail(userDetails.getUsername())
                .orElseThrow(
                        () -> new CustomException(Result.FAIL)
                );
        return user;
    }

    private Content getContent(Long contentId) {
        return contentRepository.findById(contentId)
                .orElseThrow(
                        () -> new CustomException(Result.FAIL)
                );
    }

    // validate
    private void validateAddEmotion(EmotionDto.AddEmotionDto request, Long contentId) {
        if (!contentRepository.existsById(contentId)){
            throw new CustomException(Result.NOT_FOUND_CONTENT);
        }
        if(request.getEmotionStatus()>=6){
            throw new CustomException(Result.NOT_SUPPORTED_EMOTION_STATUS);
        }
    }
}
