package dnd.diary.controller.mission;

import java.util.List;

import org.locationtech.jts.io.ParseException;
import org.springframework.web.bind.annotation.*;

import dnd.diary.dto.group.MissionCreateRequest;
import dnd.diary.response.CustomResponseEntity;
import dnd.diary.response.mission.MissionResponse;
import dnd.diary.service.group.MissionService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/mission")
@RequiredArgsConstructor
public class MissionController {

	private final MissionService missionService;
	private final MissionValidator missionValidator;

	@PostMapping
	public CustomResponseEntity<MissionResponse> createMission(@RequestBody MissionCreateRequest request)
			throws ParseException {
		missionValidator.checkCreateMission(request);
		return CustomResponseEntity.success(missionService.createMission(request));
	}

	@DeleteMapping
	public CustomResponseEntity<Void> deleteMission(@RequestParam Long missionId) {
		missionService.deleteMission(missionId);
		return CustomResponseEntity.success();
	}

	@GetMapping
	public CustomResponseEntity<List<MissionResponse>> getMissionList(@RequestParam int missionStatus) {
		return CustomResponseEntity.success(missionService.getMissionList(missionStatus));
	}
}
