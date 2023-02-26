package dnd.diary.response.group;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupStarListResponse {
	private Long groupId;   // 그룹 ID
	private String groupName;   // 그룹 이름
	private String groupImageUrl;
}