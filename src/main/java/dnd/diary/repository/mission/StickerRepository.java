package dnd.diary.repository.mission;

import dnd.diary.domain.sticker.Sticker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StickerRepository extends JpaRepository<Sticker, Long> {

    Boolean existsByStickerName(String stickerName);
    Boolean existsByStickerLevel(Long stickerLevel);
    Sticker findByStickerLevel(Long stickerLevel);
}
