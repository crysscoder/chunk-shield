# ChunkShield

![Paper](https://img.shields.io/badge/Paper-1.21.11-22c55e?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-21-f97316?style=for-the-badge&logo=openjdk)
![Version](https://img.shields.io/badge/version-1.0.1-111827?style=for-the-badge)
![License](https://img.shields.io/badge/license-MIT-2563eb?style=for-the-badge)

Защита чанка через кастомный предмет.

## Версия

ChunkShield 1.0.1

Paper 1.21.11  
API 1.21.11-R0.1-SNAPSHOT  
Java 21

## Команды

`/chunkshield give` - получить предмет защиты
`/chunkshield claim` - занять текущий чанк
`/chunkshield unclaim` - снять защиту
`/chunkshield info` - информация по чанку

## Permission

`chunkshield.use`
`chunkshield.admin`
`chunkshield.bypass`
Admin и bypass по умолчанию доступны op.

## Функции

- предметом можно защитить текущий чанк;
- чужие игроки не могут ломать и ставить блоки;
- взрывы и огонь не ломают защищённый чанк;
- владелец может снять защиту командой.

## Сборка

```bash
./gradlew build
```

Готовый `.jar` будет в `build/libs/`.
