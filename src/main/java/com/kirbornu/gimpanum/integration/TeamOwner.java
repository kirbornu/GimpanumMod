package com.kirbornu.gimpanum.integration;

import java.util.UUID;

/**
 * Команда FTB в том виде, в каком её понимает клеймовая система.
 *
 * <p>Клеймы Open Parties and Claims принадлежат игроку, а не команде, поэтому
 * захваченная Контрольная точка оформляется на владельца команды. Отображаемое
 * имя команды нужно отдельно: игроки узнают точку по названию экипажа, а не по
 * нику того, кто этот экипаж создал.
 *
 * @param teamName    отображаемое имя команды
 * @param ownerId     владелец команды — на него оформляется клейм
 * @param fallbackColor цвет самой команды FTB; идёт в дело, если у владельца не
 *                    удалось прочитать цвет его клеймов
 */
public record TeamOwner(String teamName, UUID ownerId, int fallbackColor) {
}
