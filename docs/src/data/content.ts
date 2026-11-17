export const repoUrl = 'https://github.com/ducng99/alarme';
export const releasesUrl = `${repoUrl}/releases/latest`;

export interface Item {
  icon: string;
  title: string;
  body: string;
}

export const missions: Item[] = [
  { icon: 'material-symbols:calculate-outline-rounded', title: 'Math', body: 'Solve arithmetic questions, from multiple choice up to six keypad difficulty levels.' },
  { icon: 'material-symbols:grid-view-outline-rounded', title: 'Memory', body: 'Remember the highlighted tiles and tap them back on grids up to 7 × 7.' },
  { icon: 'material-symbols:vibration-rounded', title: 'Shake', body: 'Shake your phone the given number of times.' },
  { icon: 'material-symbols:directions-walk-rounded', title: 'Steps', body: 'Get out of bed and walk, counted by the hardware step counter.' },
  { icon: 'material-symbols:sports-gymnastics-rounded', title: 'Squats', body: 'Do squats while holding your phone, verified with the light sensor.' },
  { icon: 'material-symbols:keyboard-outline-rounded', title: 'Typing', body: 'Type motivational, study or your own sentences exactly.' },
  { icon: 'material-symbols:barcode-scanner-rounded', title: 'Barcode', body: 'Scan a barcode somewhere far from your bed, like the toothpaste in the bathroom.' },
  { icon: 'material-symbols:photo-camera-outline-rounded', title: 'Photo', body: 'Take a photo of a place away from your bed.' },
  { icon: 'material-symbols:image-search-rounded', title: 'Find an object', body: 'Find and photograph a chosen object, recognised on-device by ML Kit.' },
  { icon: 'material-symbols:record-voice-over-outline-rounded', title: 'Read aloud', body: 'Say the shown words out loud, checked by on-device speech recognition.' },
];

export const features: Item[] = [
  { icon: 'material-symbols:notifications-active-outline-rounded', title: 'Rings on time, every time', body: 'Exact AlarmManager alarms that survive reboots, time-zone changes, app updates and even a reboot before you unlock.' },
  { icon: 'material-symbols:volume-up-outline-rounded', title: 'Gentle, then serious', body: 'Start quiet and ramp up, then switch to a louder backup sound if you have not reacted after 40 seconds.' },
  { icon: 'material-symbols:task-alt-rounded', title: 'Wake-up check', body: 'Rings again after you dismiss to make sure you actually got up.' },
  { icon: 'material-symbols:snooze-outline-rounded', title: 'Snooze with limits', body: 'Pick the snooze length and how many times you are allowed to snooze.' },
  { icon: 'material-symbols:event-busy-outline-rounded', title: 'Skip once', body: 'Day off tomorrow? Skip the next occurrence without turning the alarm off.' },
  { icon: 'material-symbols:bolt-outline-rounded', title: 'Quick alarms', body: 'A one-off alarm counted from now, set in two taps. Perfect for naps.' },
  { icon: 'material-symbols:music-note-rounded', title: 'Your sounds', body: 'Bundled tones, device ringtones, imported songs or a recorded voice memo. Or shuffle them.' },
  { icon: 'material-symbols:record-voice-over-outline-rounded', title: 'Spoken memo', body: 'Show a memo while the alarm rings and have the time and memo read aloud.' },
  { icon: 'material-symbols:widgets-outline-rounded', title: 'Home screen widget', body: 'See the next alarm and how long until it rings at a glance.' },
  { icon: 'material-symbols:do-not-disturb-on-outline-rounded', title: 'Through Do Not Disturb', body: 'Alarms sound even with Do Not Disturb on, right over the lock screen.' },
  { icon: 'material-symbols:palette-outline-rounded', title: 'Material You', body: 'A modern Material 3 design that follows your wallpaper colours, in light and dark.' },
  { icon: 'material-symbols:backup-outline-rounded', title: 'Local backup', body: 'Export alarms and your own sounds to a single file and restore them on a new phone.' },
];

export const antiCheat: Item[] = [
  { icon: 'material-symbols:power-settings-new-rounded', title: 'Prevent phone turn-off', body: 'Pulls the alarm back when you try to power off or leave the ring screen.' },
  { icon: 'material-symbols:delete-forever-outline-rounded', title: 'Prevent uninstall', body: 'While enabled, Alarme cannot be uninstalled at 6 AM.' },
  { icon: 'material-symbols:timer-outline-rounded', title: 'Last-chance timer', body: 'Missions have a time limit. Run out and the alarm starts ringing again.' },
  { icon: 'material-symbols:health-metrics-outline-rounded', title: 'Diagnostics', body: 'Checks battery optimisation, standby buckets and vendor autostart so nothing delays your alarm.' },
];

export const privacy: Item[] = [
  { icon: 'material-symbols:wifi-off-rounded', title: 'No network permission', body: 'The app cannot talk to the internet at all.' },
  { icon: 'material-symbols:visibility-off-outline-rounded', title: 'No analytics', body: 'No tracking, no telemetry, no crash reporting services.' },
  { icon: 'material-symbols:money-off-rounded', title: 'No ads, no paywall', body: 'Every feature is free. No subscriptions, no premium tier.' },
  { icon: 'material-symbols:memory-outline-rounded', title: 'On-device ML', body: 'Barcode, object and speech recognition all run locally.' },
];
