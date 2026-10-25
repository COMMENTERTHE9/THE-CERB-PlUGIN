package GUIs.menu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Player Menu 2.1: content appears from the centre column outwards, within 4 ticks (200 ms). */
class MenuRevealTest {

    @Test
    void centreFirstEdgesLast() {
        int[] expected = {4, 4, 3, 2, 1, 2, 3, 4, 4};
        for (int c = 0; c < 9; c++) assertEquals(expected[c], MenuManager.revealTick(c), "column " + c);
    }

    @Test
    void everyRevealFitsInsideTheAnimationLimit() {
        for (int c = 0; c < 9; c++) {
            int t = MenuManager.revealTick(c);
            assertEquals(true, t >= 1 && t <= MenuAnimator.MAX_TICKS, "column " + c + " tick " + t);
        }
    }
}
