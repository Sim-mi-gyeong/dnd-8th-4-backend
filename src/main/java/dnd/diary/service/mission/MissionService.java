package dnd.diary.service.mission;

import static dnd.diary.domain.mission.DateUtil.convertLocalDateTimeZone;
import static dnd.diary.domain.sticker.StickerLevel.getSticker;
import static dnd.diary.enumeration.Result.*;

import java.time.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dnd.diary.domain.mission.UserAssignMission;
import dnd.diary.domain.sticker.StickerGroup;
import dnd.diary.domain.user.UserJoinGroup;
import dnd.diary.request.mission.MissionCheckLocationRequest;
import dnd.diary.request.mission.MissionListByMapRequest;
import dnd.diary.repository.mission.StickerGroupRepository;
import dnd.diary.response.mission.MissionCheckContentResponse;
import dnd.diary.response.mission.MissionCheckLocationResponse;
import dnd.diary.service.content.ContentService;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dnd.diary.domain.group.Group;
import dnd.diary.domain.mission.Mission;
import dnd.diary.domain.mission.MissionStatus;
import dnd.diary.domain.user.User;
import dnd.diary.request.group.MissionCreateRequest;
import dnd.diary.exception.CustomException;
import dnd.diary.repository.group.GroupRepository;
import dnd.diary.repository.mission.MissionRepository;
import dnd.diary.repository.mission.UserAssignMissionRepository;
import dnd.diary.repository.user.UserRepository;
import dnd.diary.response.mission.MissionResponse;
import dnd.diary.service.user.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class MissionService {

	private final MissionRepository missionRepository;
	private final GroupRepository groupRepository;
	private final UserRepository userRepository;
	private final UserAssignMissionRepository userAssignMissionRepository;
	private final StickerGroupRepository stickerGroupRepository;

	private final UserService userService;
	private final ContentService contentService;
	private final StickerService stickerService;

//	private final int MISSION_DISTANCE_LIMIT = 50;
	private final int MISSION_DISTANCE_LIMIT = 200;
	private final int LEVEL_UP_DEGREE = 3;
	private final Long MISSION_DEFAULT_D_DAY = 365L;

	// 미션 생성
	@Transactional
	public MissionResponse createMission(MissionCreateRequest request, Long userId) throws ParseException {
		User user = userService.getUser(userId);
		Group group = findGroup(request.getGroupId());

		MissionStatus missionStatus = MissionStatus.READY;

		Mission mission = null;
		String pointWKT = String.format("POINT(%s %s)", request.getLatitude(), request.getLongitude());
		Point point = (Point) new WKTReader().read(pointWKT);

		// 미션 기간을 설정하지 않은 경우 - 항상 ACTIVE
		if (!request.getExistPeriod()) {
			missionStatus = MissionStatus.ACTIVE;
			mission = Mission.toEntity(
					user, group, request.getMissionName(), request.getMissionNote()
					, request.getExistPeriod()
					, convertLocalDateTimeZone(LocalDate.now().atStartOfDay(), ZoneOffset.UTC, ZoneId.of("Asia/Seoul")), null
					, request.getMissionLocationName(), request.getMissionLocationAddress()
					, request.getLatitude(), request.getLongitude()
					, request.getMissionColor(), missionStatus, point);

		} else {
			LocalDateTime today = LocalDateTime.now(ZoneId.of("Asia/Seoul"));
			// 미션 시작일 < 오늘 -> 미션 진행중 상태
			if (today.isAfter(convertLocalDateTimeZone(request.getMissionStartDate().atStartOfDay().minusHours(9), ZoneOffset.UTC, ZoneId.of("Asia/Seoul")))) {
				missionStatus = MissionStatus.ACTIVE;
			}
			// 미션 종료일 < 오늘 -> 미션 종료 상태
			if (today.isAfter(convertLocalDateTimeZone(request.getMissionEndDate().atStartOfDay().plusDays(1).minusHours(9), ZoneOffset.UTC, ZoneId.of("Asia/Seoul")))) {
				missionStatus = MissionStatus.FINISH;
			}
			// 서버 기준 시간 + 9 -> 00:00 / 23:59 로
			mission = Mission.toEntity(user, group, request.getMissionName(), request.getMissionNote()
					, request.getExistPeriod()
					, convertLocalDateTimeZone(request.getMissionStartDate().atStartOfDay(), ZoneOffset.UTC, ZoneId.of("Asia/Seoul"))
					, convertLocalDateTimeZone(request.getMissionEndDate().atTime(23, 59, 59), ZoneOffset.UTC, ZoneId.of("Asia/Seoul"))
					, request.getMissionLocationName(), request.getMissionLocationAddress()
					, request.getLatitude(), request.getLongitude()
					, request.getMissionColor(), missionStatus, point);

		}
		missionRepository.save(mission);
		log.info("mission startDate : {}", mission.getMissionStartDate());

		// 그룹에 속한 구성원 모두에게 미션 할당
		List<UserJoinGroup> userJoinGroupList = group.getUserJoinGroups();
		for (UserJoinGroup userJoinGroup : userJoinGroupList) {
			User groupUser = userJoinGroup.getUser();
			UserAssignMission userAssignMission = UserAssignMission.toEntity(groupUser, mission);
			userAssignMissionRepository.save(userAssignMission);
		}

		Long missionDday;
		if (!request.getExistPeriod()) {
			missionDday = MISSION_DEFAULT_D_DAY;
		} else {
			Period diff = Period.between(LocalDate.now(), request.getMissionEndDate());
			missionDday = Long.valueOf(diff.getDays());
		}

		UserAssignMission userAssignMission = userAssignMissionRepository.findByUserIdAndMissionId(user.getId(), mission.getId());

		return MissionResponse.builder()
			.missionId(mission.getId())
			.missionName(mission.getMissionName())
			.missionNote(mission.getMissionNote())
			.createUserId(user.getId())
			.createUserName(user.getNickName())
			.createUserProfileImageUrl(user.getProfileImageUrl())

			.groupId(group.getId())
			.groupName(group.getGroupName())
			.groupImageUrl(group.getGroupImageUrl())

			.existPeriod(request.getExistPeriod())
			.missionStartDate(
					mission.getMissionStartDate() != null ? String.valueOf(mission.getMissionStartDate()).substring(0, 10).replace("-", ".") : String.valueOf(mission.getMissionStartDate())
			)
			.missionEndDate(mission.getMissionEndDate() != null ? String.valueOf(mission.getMissionEndDate()).substring(0, 10).replace("-", ".") : "ing")
			.missionStatus(missionStatus)

			.missionLocationName(mission.getMissionLocationName())
			.missionLocationAddress(mission.getMissionLocationAddress())
			.latitude(mission.getLatitude())
			.longitude(mission.getLongitude())

			.missionDday(missionDday)
			.missionColor(mission.getMissionColor())

			.userAssignMissionInfo(getUserAssignMissionInfo(user, mission, userAssignMission))

			.build();
	}
	
	// 미션 삭제
	@Transactional
	public void deleteMission(Long missionId, Long userId) {
		User user = userService.getUser(userId);
		Mission mission = missionRepository.findMissionByIdAndDeletedYn(missionId, false);
		if (mission == null) {
			new CustomException(NOT_FOUND_MISSION);
		};

		if (!user.getId().equals(mission.getMissionCreateUser().getId())) {
			throw new CustomException(FAIL_DELETE_MISSION);
		}

		// 미션 참여자(그룹 구성원) 에게 할당된 미션 삭제 처리
		List<UserAssignMission> userAssignMissionList = mission.getUserAssignMissions();
		userAssignMissionRepository.deleteAll(userAssignMissionList);

		// 미션 삭제 처리
		mission.deleteMissionByColumn();
	}
	
	// 미션 위치 인증
	@Transactional
	public MissionCheckLocationResponse checkMissionLocation(Long userId, MissionCheckLocationRequest request) {

		// 유저가 가진 미션이 맞는지 확인
		User user = userService.getUser(userId);
		List<UserAssignMission> userAssignMissionList = user.getUserAssignMissions();
		List<Long> userAssignMissionIdList = new ArrayList<>();
		List<Long> userAssignMissionGroupIdList = new ArrayList<>();
		userAssignMissionList.forEach(
				userAssignMission -> {
					userAssignMissionIdList.add(userAssignMission.getMission().getId());
					userAssignMissionGroupIdList.add(userAssignMission.getMission().getGroup().getId());
				}
		);
		// 해당 그룹의 미션이 맞는지 확인
		if (!userAssignMissionGroupIdList.contains(request.getGroupId())) {
			throw new CustomException(INVALID_GROUP_MISSION);
		}
		// 유저가 가진 미션이 맞는지 확인
		if (!userAssignMissionIdList.contains(request.getMissionId())) {
			throw new CustomException(INVALID_USER_MISSION);
		}

		Mission targetMission = missionRepository.findMissionByIdAndDeletedYn(request.getMissionId(), false);
		if (targetMission == null) {
			throw new CustomException(NOT_FOUND_MISSION);
		}

		// 미션 진행 기간인지 확인
		if (targetMission.getMissionStatus() != MissionStatus.ACTIVE) {
			throw new CustomException(INVALID_MISSION_PERIOD);
		}

		// 미션 위치 기준 현재 자신의 위치가 반경 50m 이내에 있는지 체크
		boolean checkLocationMissionFlag = false;
		Double checkDistance = distance(request.getCurrLatitude(), request.getCurrLongitude()
				, targetMission.getLatitude(), targetMission.getLongitude());

		UserAssignMission checkUserAssignMission = null;
		for (UserAssignMission userAssignMission : targetMission.getUserAssignMissions()) {
			if (user.getId().equals(userAssignMission.getUser().getId())) {
				checkUserAssignMission = userAssignMission;

				if (checkDistance.intValue() <= MISSION_DISTANCE_LIMIT) {
					checkLocationMissionFlag = true;
					checkUserAssignMission.completeLocationCheck();
					user.updateSubLevel();
				}
				break;
			}
		}

		return MissionCheckLocationResponse.builder()
				.missionId(targetMission.getId())
				.distance(checkDistance.intValue())
				.locationCheck(checkLocationMissionFlag)
				.contentCheck(checkUserAssignMission.getContentCheck())
				.isComplete(checkUserAssignMission.getIsComplete())
				.build();
	}

	private static double distance(double lat1, double lon1, double lat2, double lon2){
		double theta = lon1 - lon2;
		double dist = Math.sin(deg2rad(lat1))* Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1))*Math.cos(deg2rad(lat2))*Math.cos(deg2rad(theta));

		dist = Math.acos(dist);
		dist = rad2deg(dist);

		dist = dist * 60 * 1.1515 * 1609.344;

		return dist;  // 단위 m
	}

	// 10진수를 radian 으로 변환
	private static double deg2rad(double deg){
		return (deg * Math.PI / 180.0);
	}
	// radian 을 10진수로 변환
	private static double rad2deg(double rad){
		return (rad * 180 / Math.PI);
	}

	// 미션 게시물 인증
	@Transactional
	public MissionCheckContentResponse checkMissionContent(Long userId, List<MultipartFile> multipartFiles, Long missionId, String content) throws ParseException {

		User user = userService.getUser(userId);
		Mission targetMission = missionRepository.findMissionByIdAndDeletedYn(missionId, false);

		if (targetMission == null) {
			throw new CustomException(NOT_FOUND_MISSION);
		}

		// 미션 진행 기간인지 확인
		if (targetMission.getMissionStatus() != MissionStatus.ACTIVE) {
			throw new CustomException(INVALID_MISSION_PERIOD);
		}
		UserAssignMission targetUserAssignMission = userAssignMissionRepository.findByUserIdAndMissionId(user.getId(), missionId);
		// 위치 인증이 우선 진행된 미션인지 확인
		if (!targetUserAssignMission.getLocationCheck()) {
			throw new CustomException(NOT_CHECK_MISSION_LOCATION);
		}
		// 이미 완료된 미션인 경우
		if (targetUserAssignMission.getIsComplete()) {
			throw new CustomException(ALREADY_COMPLETE_MISSION);
		}

		contentService.createContent(
				userId, multipartFiles, targetMission.getGroup().getId(), content,
				targetMission.getLatitude(), targetMission.getLongitude(), targetMission.getMissionLocationName()
		);

		// 유저 미션 게시글 인증 상태 업데이트
		targetUserAssignMission.completeContentCheck();

		// 미션 인증 레벨 업데이트
		user.updateSubLevel();

		Boolean isGetNewSticker = false;
		Long currMainLevel = user.getMainLevel();
		Long stickerGroupId = null;
		String stickerGroupName = null;

		if (user.getSubLevel().intValue() == LEVEL_UP_DEGREE) {
			user.updateLevel();
			// 스티커를 획득할 수 있는 mainLevel 달성 시 획득 처리
			if (getSticker(user.getMainLevel().intValue())) {
				stickerService.acquisitionSticker(user);
				isGetNewSticker = true;
				currMainLevel = user.getMainLevel();
				// mainLevel 에 해당하는 스티커 그룹의 ID
				StickerGroup stickerGroup = stickerGroupRepository.findByStickerGroupLevel(user.getMainLevel());
				stickerGroupId = stickerGroup.getId();
				stickerGroupName = stickerGroup.getStickerGroupName();
			}
		}

		return MissionCheckContentResponse.builder()
				.missionId(targetMission.getId())
				.locationCheck(targetUserAssignMission.getLocationCheck())
				.contentCheck(targetUserAssignMission.getContentCheck())
				.isComplete(targetUserAssignMission.getIsComplete())

				.isGetNewSticker(isGetNewSticker)   // true 일 경우에만 getNewStickerGroupId 가 null 이 아닌 값
				.currMainLevel(currMainLevel)
				.getNewStickerGroupId(stickerGroupId)
				.getNewStickerGroupName(stickerGroupName)
				.build();
	}

	private User getUser(UserDetails userDetails) {
		User user = userRepository.findOneWithAuthoritiesByEmail(userDetails.getUsername())
				.orElseThrow(
						() -> new CustomException(NOT_FOUND_USER)
				);
		return user;
	}

	// 미션 상태별 목록 조회 (0 : 전체, 1 : 시작 전, 2 : 진행중, 3 : 종료)
	public List<MissionResponse> getMissionList(Long userId, int missionStatus) {

		User user = userService.getUser(userId);
		MissionStatus findMissionStatus = MissionStatus.getName(missionStatus);
		List<UserAssignMission> userAssignMissionList = user.getUserAssignMissions();
		List<MissionResponse> missionResponseList = new ArrayList<>();

		for (UserAssignMission userAssignMission : userAssignMissionList) {
			Mission mission = userAssignMission.getMission();

			if (userAssignMission.getIsComplete()) continue;

			if (mission.isDeletedYn()) continue;

			if (MissionStatus.ALL == findMissionStatus) {
				MissionResponse missionResponse = toMissionResponse(mission);

				MissionResponse.UserAssignMissionInfo userAssignMissionInfo = getUserAssignMissionInfo(user, mission, userAssignMission);
				missionResponse.setUserAssignMissionInfo(userAssignMissionInfo);

				missionResponseList.add(missionResponse);
			} else {
				// 1 : 시작 전, 2 : 진행중 인 미션 중, 이미 기간이 끝났지만 배치 처리로 미션 상태 변경이 정상 동작하지 않은 경우
				if (findMissionStatus == mission.getMissionStatus()) {
					if ((MissionStatus.READY == mission.getMissionStatus() || MissionStatus.ACTIVE == mission.getMissionStatus())
						&& mission.getMissionEndDate() != null
						&& LocalDateTime.now(ZoneId.of("Asia/Seoul")).isAfter(mission.getMissionEndDate())) {
						continue;
					}
					MissionResponse missionResponse = toMissionResponse(mission);

					MissionResponse.UserAssignMissionInfo userAssignMissionInfo = getUserAssignMissionInfo(user, mission, userAssignMission);
					missionResponse.setUserAssignMissionInfo(userAssignMissionInfo);

					missionResponseList.add(missionResponse);
				}
			}
		}
		missionResponseList.sort(Comparator.comparing(MissionResponse::getMissionDday));
		return missionResponseList;
	}

	private MissionResponse.UserAssignMissionInfo getUserAssignMissionInfo(User user, Mission mission, UserAssignMission userAssignMission) {
		return MissionResponse.UserAssignMissionInfo.builder()
				.userId(user.getId())
				.userNickname(user.getNickName())
				.missionId(mission.getId())
				.locationCheck(userAssignMission.getLocationCheck())
				.contentCheck(userAssignMission.getContentCheck())
				.isComplete(userAssignMission.getIsComplete())
				.build();
	}

	// 그룹 메인 진입 페이지 내 [시작 전/진행 중] 미션 목록 조회 - 최대 4개
	public List<MissionResponse> getReadyAndActiveGroupMissionList(Long userId, int groupId) {

		User user = userService.getUser(userId);
		Group group = findGroup(Long.parseLong(String.valueOf(groupId)));

		List<MissionResponse> missionResponseList = new ArrayList<>();

		List<Mission> missionListInGroup = group.getMissions();
		for (Mission mission : missionListInGroup) {
			if (mission.getMissionStatus() == MissionStatus.READY || mission.getMissionStatus() == MissionStatus.ACTIVE) {
				MissionResponse missionResponse = toMissionResponse(mission);

				UserAssignMission userAssignMission = userAssignMissionRepository.findByUserIdAndMissionId(user.getId(), mission.getId());
				if (userAssignMission == null) {
					throw new CustomException(INVALID_USER_MISSION);
				}
				if (!userAssignMission.getIsComplete()) {   // 사용자가 완료한 미션은 제외
					MissionResponse.UserAssignMissionInfo userAssignMissionInfo = getUserAssignMissionInfo(user, mission, userAssignMission);
					missionResponse.setUserAssignMissionInfo(userAssignMissionInfo);

					missionResponseList.add(missionResponse);
				}
			}
		}

		missionResponseList.sort(Comparator.comparing(MissionResponse::getMissionDday));
		List<MissionResponse> maxFourMissionResponseList = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			if (i < missionResponseList.size()) {
				maxFourMissionResponseList.add(missionResponseList.get(i));
			}
		}
		return maxFourMissionResponseList;
	}

	// 시작 전인 미션 + 진행 중인 미션 전체
	public List<MissionResponse> getReadyAndActiveMissionList(Long userId) {
		List<MissionResponse> missionResponseList = getMissionList(userId, MissionStatus.READY.getCode());
		missionResponseList.addAll(getMissionList(userId, MissionStatus.ACTIVE.getCode()));
		return missionResponseList;
	}

	public MissionResponse getMission(Long missionId, Long userId) {
		User user = userService.getUser(userId);
		Mission mission = missionRepository.findMissionByIdAndDeletedYn(missionId, false);
		if (mission == null) {
			throw new CustomException(NOT_FOUND_MISSION);
		}
		UserAssignMission userAssignMission = userAssignMissionRepository.findByUserIdAndMissionId(user.getId(), missionId);
		if (userAssignMission == null) {
			throw new CustomException(INVALID_USER_MISSION);
		}
		MissionResponse missionResponse = toMissionResponse(mission);
		MissionResponse.UserAssignMissionInfo userAssignMissionInfo = getUserAssignMissionInfo(user, mission, userAssignMission);
		missionResponse.setUserAssignMissionInfo(userAssignMissionInfo);

		return missionResponse;
	}

	private MissionResponse toMissionResponse(Mission mission) {
		return MissionResponse.builder()
			.missionId(mission.getId())
			.missionName(mission.getMissionName())
			.missionNote(mission.getMissionNote())

			.createUserId(mission.getMissionCreateUser().getId())
			.createUserName(mission.getMissionCreateUser().getNickName())
			.createUserProfileImageUrl(mission.getMissionCreateUser().getProfileImageUrl())

			.groupId(mission.getGroup().getId())
			.groupName(mission.getGroup().getGroupName())
			.groupImageUrl(mission.getGroup().getGroupImageUrl())

			.existPeriod(mission.getExistPeriod())
			.missionStartDate(
					mission.getMissionStartDate() != null ? String.valueOf(mission.getMissionStartDate()).substring(0, 10).replace("-", ".") : String.valueOf(mission.getMissionStartDate())
			)
			.missionEndDate(mission.getMissionEndDate() != null ? String.valueOf(mission.getMissionEndDate()).substring(0, 10).replace("-", ".") : "ing")

			.missionStatus(mission.getMissionStatus())
			.missionLocationName(mission.getMissionLocationName())
			.missionLocationAddress(mission.getMissionLocationAddress())
			.latitude(mission.getLatitude())
			.longitude(mission.getLongitude())

			.missionDday(
					mission.getMissionEndDate() != null ? (long) Period.between(LocalDate.now(ZoneId.of("Asia/Seoul")), mission.getMissionEndDate().toLocalDate()).getDays() : MISSION_DEFAULT_D_DAY
			)
			.missionColor(mission.getMissionColor())
			.build();
	}

	// 유저에게 할당된 미션 중, 지도 범위 내에 존재하는 미션 목록 조회
	public List<MissionResponse> getMissionListByMap(MissionListByMapRequest missionListByMapRequest, Long userId) {

		User user = userService.getUser(userId);
		List<Mission> userMissionList = new ArrayList<>();
		user.getUserAssignMissions().forEach(
				userAssignMission -> {
					if (!userAssignMission.getIsComplete()) {   // 이미 완료한 미션은 지도 모아보기에서 제외
						userMissionList.add(userAssignMission.getMission());
					}
				}
		);
		List<MissionResponse> missionResponseList = new ArrayList<>();
		MissionListByMapRequest request = missionListByMapRequest.setStartXY();

		List<Mission> userMissionListWithInMap = missionRepository.findWithinMap(
				request.getStartLatitude(), request.getEndLatitude(), request.getStartLongitude(), request.getEndLongitude()
		);

		for (Mission mission : userMissionListWithInMap) {
			if (mission.isDeletedYn()) continue;

			if (!userMissionList.contains(mission)) {   // 유저에게 할당된 미션이 아닌 경우
				continue;
			}
			if (mission.getMissionStatus() == MissionStatus.ACTIVE || mission.getMissionStatus() == MissionStatus.READY) {
				missionResponseList.add(toMissionResponse(mission));
			}
		}
		missionResponseList.sort(Comparator.comparing(MissionResponse::getMissionDday));
		return missionResponseList;
	}

	// 완료한 미션 목록 조회
	public List<MissionResponse> getCompleteMissionList(Long userId) {
		User user = userService.getUser(userId);
		List<MissionResponse> completeMissionResponseList = new ArrayList<>();

		List<UserAssignMission> userAssignMissionList = user.getUserAssignMissions();

		for (UserAssignMission userAssignMission : userAssignMissionList) {
			if (userAssignMission.getIsComplete()) {
				MissionResponse missionResponse = toMissionResponse(userAssignMission.getMission());

				MissionResponse.UserAssignMissionInfo userAssignMissionInfo = getUserAssignMissionInfo(user, userAssignMission.getMission(), userAssignMission);
				missionResponse.setUserAssignMissionInfo(userAssignMissionInfo);

				completeMissionResponseList.add(missionResponse);
			}
		}
		return completeMissionResponseList;
	}

	private Group findGroup(Long groupId) {
		return groupRepository.findById(groupId).orElseThrow(() -> new CustomException(NOT_FOUND_GROUP));
	}
}