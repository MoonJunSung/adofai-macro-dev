# ADOFAI Timing Calculator Library

A Dance of Fire and Ice 레벨 파일의 노트 타이밍을 계산하는 Java 라이브러리입니다.

## 📋 기능

- `.adofai` 레벨 파일 파싱 (외부 라이브러리 없이 순수 Java)
- `angleData` / `pathData` 포맷 모두 지원
- BPM 및 이벤트 처리:
  - `SetSpeed` (BPM 변속)
  - `Twirl` (방향 전환)
  - `Pause` (일시 정지)
  - `Hold` (홀드)
  - `MultiPlanet` (3행성 모드)
- 노트별 정확한 타이밍(ms) 계산
- 자동 오프셋 계산 (countdownTicks + offset)

## 🚀 사용법

### 커맨드라인

```bash
javac ADOFAITimingLib.java
java ADOFAITimingLib "My Level.adofai"
```

### 코드에서 사용

```java
import java.util.List;

public class Example {
    public static void main(String[] args) throws Exception {
        // 라이브러리 인스턴스 생성
        ADOFAITimingLib lib = new ADOFAITimingLib();
        
        // 레벨 파일 로드
        lib.loadLevel("level.adofai");
        
        // 레벨 정보 조회
        ADOFAITimingLib.LevelInfo info = lib.getLevelInfo();
        System.out.println("Song: " + info.song);
        System.out.println("BPM: " + info.bpm);
        System.out.println("Total Tiles: " + info.totalTiles);
        
        // 노트 타이밍 계산
        List<Double> timings = lib.calculateNoteTimes();
        
        // 각 타일의 타이밍 출력
        for (int i = 0; i < timings.size(); i++) {
            System.out.printf("Tile %d: %.2f ms%n", i + 1, timings.get(i));
        }
        
        // 자동 오프셋 계산
        double autoOffset = lib.calculateAutoOffset();
        System.out.printf("Auto Offset: %.2f ms%n", autoOffset);
    }
}
```

## 📖 API

### `ADOFAITimingLib`

| 메서드 | 설명 |
|--------|------|
| `loadLevel(String filePath)` | .adofai 레벨 파일을 로드합니다 |
| `getLevelInfo()` | 레벨 정보(곡명, BPM, 오프셋 등)를 반환합니다 |
| `calculateNoteTimes()` | 모든 노트의 타이밍(ms)을 계산하여 리스트로 반환합니다 |
| `calculateAutoOffset()` | 자동 오프셋(countdownTicks + offset)을 계산합니다 |

### `LevelInfo`

| 필드 | 타입 | 설명 |
|------|------|------|
| `song` | String | 곡 제목 |
| `artist` | String | 아티스트 |
| `author` | String | 레벨 제작자 |
| `bpm` | double | 기본 BPM |
| `offset` | int | 오프셋 (ms) |
| `pitch` | double | 피치 (%) |
| `countdownTicks` | int | 카운트다운 틱 수 |
| `totalTiles` | int | 총 타일 수 |
| `totalDuration` | double | 총 길이 (ms) |

## 📁 출력 예시

```
=== Level Info ===
Song: Example Song
Artist: Example Artist
Author: LevelMaker
BPM: 120.00
Offset: 0 ms
Pitch: 100%
Countdown: 4 ticks
Total Tiles: 256
Duration: 128.50 seconds

=== Note Timings (first 20) ===
Tile   1:   500.00 ms
Tile   2:  1000.00 ms
Tile   3:  1500.00 ms
...
```

## ⚙️ 기술 상세

### 지원하는 pathData 문자

| 문자 | 각도 | 타입 |
|------|------|------|
| R | 0° | 절대 |
| E | 45° | 절대 |
| U | 90° | 절대 |
| Q | 135° | 절대 |
| L | 180° | 절대 |
| Z | 225° | 절대 |
| D | 270° | 절대 |
| C | 315° | 절대 |
| ! | Midspin | 특수 |
| ... | ... | ... |

### 알고리즘

1. pathData → 각도 변환 (상대/절대 각도 처리)
2. 이벤트 적용 (SetSpeed, Twirl, Pause, Hold, MultiPlanet)
3. BPM 전파 및 방향 계산
4. 각도 → 시간(ms) 변환: `time = (angle / 180) * (60 / bpm) * 1000`

## 📜 라이선스

MIT License
