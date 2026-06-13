package ui.helpers;

import java.time.LocalDateTime;

public final class WelcomeGreetingHelper {
    private WelcomeGreetingHelper() {
    }

    public record Greeting(String title, String subtitle) {
    }

    public static Greeting currentGreeting() {
        LocalDateTime now = LocalDateTime.now(StoreTimeZoneHelper.getStoreZone());
        int hour = now.getHour();
        int variant = (now.getMinute() / 10) % 3;
        if (hour < 5) {
            return new Greeting(
                    "Welcome, Night Crew",
                    variant == 0 ? "Quiet shift, clean starts, steady systems."
                            : variant == 1 ? "SmartStock is ready whenever you are."
                            : "Late hours still deserve a smooth login.");
        } else if (hour < 12) {
            return new Greeting(
                    variant == 1 ? "Morning, SmartStock Team" : "Good Morning",
                    variant == 0 ? "Let us get the day opened cleanly."
                            : variant == 1 ? "Coffee checked, inventory ready."
                            : "Fresh day, clear counts, confident sales.");
        } else if (hour == 12) {
            return new Greeting(
                    variant == 0 ? "What's for Lunch? 🍽"
                            : variant == 1 ? "Lunch Time Already? 🥪"
                            : "Midday Check-In ☀",
                    variant == 0 ? "Take care of the rush, then take care of yourself."
                            : variant == 1 ? "A smooth system makes a better lunch break."
                            : "Half the day down, plenty of wins left.");
        } else if (hour < 16) {
            return new Greeting(
                    variant == 2 ? "Afternoon Flow" : "Good Afternoon",
                    variant == 0 ? "Keep the store moving with clean data."
                            : variant == 1 ? "Steady scans, steady stock, steady sales."
                            : "The afternoon shift is ready to roll.");
        } else if (hour < 18) {
            return new Greeting(
                    variant == 0 ? "Waiting for 5 PM?" : "Final Stretch",
                    variant == 0 ? "Close time is close, but SmartStock is awake."
                            : variant == 1 ? "Finish sharp, then head out proud."
                            : "One clean close makes tomorrow easier.");
        }
        return new Greeting(
                variant == 1 ? "Evening Wrap-Up" : "Good Evening",
                variant == 0 ? "Settle in and keep the close simple."
                        : variant == 1 ? "Evening pace, clean screens, calm totals."
                        : "Let us make the last tasks feel lighter.");
    }
}
