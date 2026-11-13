package vivaleve.proj.model.enums;

public enum MoodType {
    OTIMO(5), BEM(4), MAIS_OU_MENOS(3), MAL(2), DEPRESSIVO(1), ANSIOSO(0);

    private final int score;
    MoodType(int score) { this.score = score; }
    public int getScore() { return score; }
}
