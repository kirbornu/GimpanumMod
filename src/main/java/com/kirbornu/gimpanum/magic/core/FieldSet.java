package com.kirbornu.gimpanum.magic.core;

import java.util.List;

/**
 * Именованный набор полей — то, чем катализатор во второй руке меняет смысл рун.
 *
 * @param turnAngle      свой угол поворота или {@code null}, если общий
 * @param stepMultiplier свой множитель шага или {@code null}, если общий
 */
public record FieldSet(String name, List<ArcaneField> fields, Double turnAngle, Double stepMultiplier) {

    public FieldSet {
        fields = List.copyOf(fields);
    }

    public boolean isEmpty() {
        return fields.isEmpty();
    }

    public TurtleSettings applyTo(TurtleSettings settings) {
        return settings.with(turnAngle, stepMultiplier);
    }

    /**
     * Чей аркан у этой точки.
     *
     * <p>Внутри полей побеждает самое узкое: маленькое поле внутри большого —
     * это уточнение, и иначе до него было бы не добраться. Снаружи всех полей
     * радиус не значит ничего, решает ближайший центр. Ничьи в обоих случаях
     * отдаются полю, стоящему раньше в списке: так порядок в файле — это
     * осознанный способ разрешить спор, а не случайность.
     *
     * @return id аркана; рунописи без аркана не бывает
     * @throws IllegalStateException если в наборе нет полей
     */
    public String resolve(Vector3 point) {
        ArcaneField best = null;
        double bestDistance = 0;
        for (ArcaneField field : fields) {
            if (!field.contains(point)) {
                continue;
            }
            double distance = point.distanceTo(field.center());
            if (best == null || field.radius() < best.radius()
                    || field.radius() == best.radius() && distance < bestDistance) {
                best = field;
                bestDistance = distance;
            }
        }
        if (best != null) {
            return best.arcane();
        }
        for (ArcaneField field : fields) {
            double distance = point.distanceTo(field.center());
            if (best == null || distance < bestDistance) {
                best = field;
                bestDistance = distance;
            }
        }
        if (best == null) {
            throw new IllegalStateException("в наборе полей " + name + " нет ни одного поля");
        }
        return best.arcane();
    }
}
