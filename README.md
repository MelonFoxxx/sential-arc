# Sentinel — античіт + Anti-Xray + система жалоб (Paper 1.21.10, JDK 21)

## Збірка
Потрібні JDK 21 та Maven:

    mvn clean package

Готовий файл: `target/Sentinel.jar` → покладіть у `plugins/` і перезапустіть сервер.

> Код не компілювався в середовищі, де він створений (там не було javac/Maven/мережі).
> Якщо при збірці буде помилка — надішліть її текст, і я виправлю.

## Права (LuckPerms)
    /lp group admin permission set reports.view true
    /lp group admin permission set reports.delete true
    /lp group admin permission set sentinel.alerts true
    /lp group owner permission set sentinel.bypass true     # обхід античіта

| Право | Що дає | За замовчуванням |
|---|---|---|
| sentinel.report | /report | всі |
| reports.view | /reports, телепорт до порушника, сповіщення про нові жалоби | OP |
| reports.delete | видалення жалоб (Shift+ПКМ) | OP |
| sentinel.alerts | сповіщення про спрацювання античіта | OP |
| sentinel.bypass | обхід усіх перевірок | ніхто |
| sentinel.admin | /sentinel reload\|vl\|reset | OP |

## Жалоби
* `/report [нік]` — меню: кнопка «Нікнейм» (пишете нік у чат), вибір причини, кнопка «Опис» (пишете опис у чат), «Надіслати».
  У чаті можна написати `cancel`, щоб повернутись у меню. Для останньої причини («Інше») опис обов'язковий.
* `/reports` — голови гравців, на яких скаржились. Наведіть курсор: дата, хто скаржився, причина, опис.
  **ЛКМ** — телепорт до гравця (якщо офлайн — до його останньої позиції). **Shift+ПКМ** — видалити жалоби.
* Дані зберігаються у `plugins/Sentinel/reports.yml`. Причини та кулдаун — у `config.yml`.

## Anti-Xray
1. **Справжній захист** — вбудований Anti-Xray Paper (ховає руди від клієнта в пакетах чанків).
   У `config/paper-world-defaults.yml`:

       anti-xray:
         enabled: true
         engine-mode: 2
         hidden-blocks: [copper_ore, deepslate_copper_ore, gold_ore, deepslate_gold_ore, iron_ore, deepslate_iron_ore, coal_ore, deepslate_coal_ore, lapis_ore, deepslate_lapis_ore, mossy_cobblestone, obsidian, chest, diamond_ore, deepslate_diamond_ore, redstone_ore, deepslate_redstone_ore, clay, emerald_ore, deepslate_emerald_ore, ender_chest]

   Sentinel при старті перевіряє цей файл і пише попередження, якщо Anti-Xray вимкнено.
2. **Статистичний детектор** у Sentinel: якщо частка рідкісних руд серед останніх 100 видобутих блоків
   занадто висока — адміни отримують сповіщення (без автопокарання, див. `alert-only`).

## Перевірки античіта
Speed, Fly, HighJump/Step, NoFall, Jesus, Phase/NoClip, Timer, InvMove, InvClickSpeed (ChestStealer),
Reach, KillAura/Aimbot (кут), HitThroughWall, MultiAura, AutoClicker, BlockReach, Nuker,
BlockDirection, FastPlace, Scaffold, Xray (статистика).
Кожна вмикається/вимикається та налаштовується в `config.yml`. Порушення (VL) спадають по 1 раз на 30 с;
при досягненні `punishment.max-vl` виконуються `punishment.commands` (за замовчуванням — kick).

## Обмеження (важливо)
* 100% захисту від усіх читів не існує. Цей плагін працює на подіях Bukkit/Paper, без перехоплення пакетів
  (ProtocolLib/PacketEvents), тому не бачить частину обходів (Velocity/Anti-KB, NoSlow, Criticals, FastEat,
  точні Reach-хаки в межах 3.8 блока тощо). Для максимального покриття використовуйте разом із Grim/Vulcan.
* Налаштуйте `tolerance`/пороги під свій сервер: при високому пінгу, на льоду, з кастомними плагінами
  швидкості (які змінюють атрибути напряму) можливі хибні спрацювання. Спершу тримайте
  `max-vl` великим і дивіться сповіщення.
