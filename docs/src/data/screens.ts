import diagnostics from '../assets/screens/diagnostics.png';
import editorMissions from '../assets/screens/editor-missions.png';
import editorSound from '../assets/screens/editor-sound.png';
import editor from '../assets/screens/editor.png';
import listDark from '../assets/screens/list-dark.png';
import list from '../assets/screens/list.png';
import missionMath from '../assets/screens/mission-math.png';
import missionPicker from '../assets/screens/mission-picker.png';
import quickAlarm from '../assets/screens/quick-alarm.png';
import ring from '../assets/screens/ring.png';
import settings from '../assets/screens/settings.png';

export const screens = {
  diagnostics,
  editorMissions,
  editorSound,
  editor,
  listDark,
  list,
  missionMath,
  missionPicker,
  quickAlarm,
  ring,
  settings,
};

export const gallery = [
  { src: list, caption: 'Your alarms at a glance' },
  { src: editor, caption: 'Set the time and repeat days' },
  { src: missionPicker, caption: 'Nine wake-up missions' },
  { src: ring, caption: 'The ring screen' },
  { src: missionMath, caption: 'Solve to dismiss' },
  { src: quickAlarm, caption: 'Quick alarms for naps' },
  { src: listDark, caption: 'Dark theme' },
];
