package dnd.diary.domain.content;

import dnd.diary.domain.BaseEntity;
import dnd.diary.domain.bookmark.Bookmark;
import dnd.diary.domain.document.ContentJoinDocumentFolder;
import dnd.diary.domain.document.Document;
import dnd.diary.domain.group.Group;
import dnd.diary.domain.user.User;
import dnd.diary.domain.comment.Comment;
import lombok.*;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;
import org.locationtech.jts.geom.Point;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@AllArgsConstructor
@Builder
@DynamicUpdate
@Where(clause = "delete_at IS NULL")
@SQLDelete(sql = "UPDATE content SET delete_at = CURRENT_TIMESTAMP where content_id = ?")
public class Content extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "content_id")
    private Long id;

    @Column(nullable = false)
    private String content;

    private Double latitude;

    private Double longitude;

    private Point point;

    private String location;

    @Column(nullable = false)
    private long views;

    @Column(nullable = false)
    private String contentLink;

    @Column(name = "delete_at", nullable = true)
    private LocalDateTime deleteAt;

    private boolean deletedYn = Boolean.FALSE;;   // 게시물 삭제 여부

    // 게시물을 작성자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 작성된 그룹
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private Group group;

    // 게시물에 포함된 이미지 목록
    @OneToMany(mappedBy = "content")
    @Builder.Default
    private List<ContentImage> contentImages = new ArrayList<>();

    // 게시물에 달린 댓글 목록
    @OneToMany(mappedBy = "content")
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    // 게시물에 달린 공감 목록
    @OneToMany(mappedBy = "content")
    @Builder.Default
    private List<Emotion> emotions = new ArrayList<>();

    // 게시물이 북마크된 정보
    @OneToMany(mappedBy = "content")
    @Builder.Default
    private List<Bookmark> bookmarks = new ArrayList<>();

    // 게시물이 포함된 모아보기 목록
    @OneToMany(mappedBy = "content")
    @Builder.Default
    private List<Document> documents = new ArrayList<>();

    // 게시물이 속한 모아보기 폴더 목록
    @OneToMany(mappedBy = "content")
    @Builder.Default
    private List<ContentJoinDocumentFolder> contentJoinDocumentFolders = new ArrayList<>();

    public void updateContentImages(List<ContentImage> contentImages){
        this.contentImages = contentImages;
    }

    public void updateContent(String content, Double latitude, Double longitude, String location, List<ContentImage> contentImages){
        this.contentImages = contentImages;
        this.content = content;
        this.latitude = latitude;
        this.longitude = longitude;
        this.location = location;
    }

    public void deleteContent() {
        this.deletedYn = true;
    }
}
