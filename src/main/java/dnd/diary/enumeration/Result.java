package dnd.diary.enumeration;

import lombok.Getter;

@Getter
public enum Result {

	OK(0, "성공"),
	FAIL(-1, "실패"),
	// 유저 관련
	NOT_FOUND_USER(2200, "존재하지 않는 사용자"),
	DUPLICATION_USER(2201, "이미 존재하는 사용자"),
	DUPLICATION_NICKNAME(2202, "이미 존재하는 닉네임"),
	NOT_MATCHED_ID_OR_PASSWORD(2203,"아이디 또는 비밀번호를 잘못 입력하였습니다."),

	// 그룹 관련
	NOT_FOUND_GROUP(2101, "존재하지 않는 그룹"),
	LOW_MIN_GROUP_NAME_LENGTH(2102, "그룹 이름 최소 글자(1자) 미만"),
	HIGH_MAX_GROUP_NAME_LENGTH(2103, "그룹 이름 최대 글자(12자) 초과"),
	HIGH_MAX_GROUP_NOTE_LENGTH(2104, "그룹 소개 최대 글자(30자) 초과"),
	NO_USER_GROUP_LIST(2105, "가입한 그룹이 없는 경우"),
	FAIL_UPDATE_GROUP(2106, "방장만 그룹 수정 가능"),
	FAIL_DELETE_GROUP(2107, "방장만 그룹 삭제 가능"),

	// 피드 관련
	CONTENT_DELETE_OK(0, "피드 삭제 성공"),
	EMOTION_DELETE_OK(0, "공감 삭제 성공"),
	COMMENT_LIKE_DELETE_OK(0, "댓글 좋아요 삭제 성공"),
	DELETE_CONTENT_FAIL(2300, "유저가 작성한 글이 아닙니다."),

	// 파일 관련
	FAIL_IMAGE_UPLOAD(2000, "파일 업로드 실패");

	private final int code;
	private final String message;

	Result(int code, String message) {
		this.code = code;
		this.message = message;
	}

	public Result resolve(int code) {
		for (Result result : values()) {
			if (result.getCode() == code) {
				return result;
			}
		}
		return null;
	}
}
