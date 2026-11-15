const byId = id => document.getElementById(id);
const priorityLabels = { LOW: 'Žemas', MEDIUM: 'Vidutinis', HIGH: 'Aukštas' };
const dateFormat = new Intl.DateTimeFormat('lt-LT', {
  timeZone: 'Europe/Vilnius', year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hourCycle: 'h23'
});
let tasks = [];
let loadedContext = byId('context').value;

function localInput(instant) {
  const parts = Object.fromEntries(dateFormat.formatToParts(new Date(instant)).map(p => [p.type, p.value]));
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}
function showMessage(message, error = false) {
  byId('message').textContent = message;
  byId('message').className = error ? 'error' : '';
}
async function api(path, method = 'GET', body) {
  let response;
  let data;
  try {
    response = await fetch(`/api${path}`, {
      method, headers: { 'X-Demo-Context': byId('context').value, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body)
    });
    data = response.status === 204 ? null : await response.json();
  } catch {
    throw new Error('Nepavyko susisiekti su serveriu arba perskaityti atsakymo. Patikrinkite, ar programa paleista.');
  }
  if (!response.ok) throw new Error(data?.message || 'Užklausos įvykdyti nepavyko. Patikrinkite įvestį.');
  return data;
}
async function perform(action) {
  document.querySelectorAll('button, input, select').forEach(e => e.disabled = true);
  try { await action(); } catch (error) { showMessage(error.message || 'Nepavyko susisiekti su serveriu.', true); }
  finally { document.querySelectorAll('button, input, select').forEach(e => e.disabled = false); }
}
function row(target, values, actions = []) {
  const tr = document.createElement('tr');
  for (const value of values) {
    const td = document.createElement('td');
    td.textContent = value;
    tr.append(td);
  }
  if (actions.length) {
    const td = document.createElement('td');
    for (const [label, action] of actions) {
      const button = document.createElement('button');
      button.type = 'button'; button.textContent = label;
      button.addEventListener('click', () => perform(action));
      td.append(button);
    }
    tr.append(td);
  }
  byId(target).append(tr);
}
function clearForms() {
  byId('task-form').reset(); byId('task-id').value = '';
  byId('availability-form').reset(); byId('availability-id').value = '';
}
function renderSchedule(schedule) {
  byId('sessions').replaceChildren(); byId('unscheduled').replaceChildren();
  const title = id => `${id} – ${tasks.find(t => t.id === id)?.title || 'Ankstesnė užduotis'}`;
  for (const session of schedule.sessions) {
    row('sessions', [title(session.taskId), dateFormat.format(new Date(session.start)),
      dateFormat.format(new Date(session.end)), (Date.parse(session.end) - Date.parse(session.start)) / 60000]);
  }
  for (const item of schedule.unscheduled) row('unscheduled', [title(item.taskId), item.minutes,
    item.minutes === 0 ? 'Visas darbas suplanuotas.' : item.reasons.join(' ')]);
  byId('schedule-state').textContent = schedule.stale
    ? 'Įvestis pakeista. Rodomas ankstesnis išsaugotas grafikas; perskaičiuokite.'
    : schedule.planningStart
      ? `Išsaugoto grafiko pradžia: ${dateFormat.format(new Date(schedule.planningStart))}; laikotarpis: ${schedule.horizonDays} d.`
      : 'Grafiko dar nėra. Paspauskite „Apskaičiuoti / perskaičiuoti“.';
}
async function refresh() {
  const [context, currentTasks, availability, schedule] = await Promise.all([
    api('/context'), api('/tasks'), api('/availability'), api('/schedule')
  ]);
  tasks = currentTasks;
  byId('settings').textContent = `${context.name}; naudotojas ${context.userId}. Europe/Vilnius. ` +
    `Sesija iki ${context.settings.sessionMaxMinutes} min., pertrauka ${context.settings.breakMinutes} min., ` +
    `dienos darbo limitas ${context.settings.dailyLimitMinutes} min. LOW < MEDIUM < HIGH.`;
  byId('tasks').replaceChildren();
  for (const task of tasks) row('tasks', [`${task.id} – ${task.title}`, dateFormat.format(new Date(task.deadline)),
    priorityLabels[task.priority], task.remainingMinutes, task.done ? 'Atlikta' : 'Neatlikta'], [
    ['Keisti', async () => {
      byId('task-id').value = task.id; byId('title').value = task.title;
      byId('deadline').value = localInput(task.deadline); byId('priority').value = task.priority;
      byId('remaining').value = task.remainingMinutes; byId('done').checked = task.done;
    }],
    ['Pašalinti', async () => { await api(`/tasks/${task.id}`, 'DELETE'); clearForms(); await refresh(); showMessage('Užduotis pašalinta. Perskaičiuokite grafiką.'); }]
  ]);
  byId('availability').replaceChildren();
  for (const interval of availability) row('availability', [interval.id, dateFormat.format(new Date(interval.start)),
    dateFormat.format(new Date(interval.end))], [
    ['Keisti', async () => {
      byId('availability-id').value = interval.id;
      byId('available-start').value = localInput(interval.start); byId('available-end').value = localInput(interval.end);
    }],
    ['Pašalinti', async () => { await api(`/availability/${interval.id}`, 'DELETE'); clearForms(); await refresh(); showMessage('Intervalas pašalintas. Perskaičiuokite grafiką.'); }]
  ]);
  renderSchedule(schedule);
  loadedContext = context.key;
}
byId('task-form').addEventListener('submit', event => {
  event.preventDefault();
  perform(async () => {
    const id = byId('task-id').value;
    await api(`/tasks${id ? '/' + id : ''}`, id ? 'PUT' : 'POST', {
      title: byId('title').value, deadline: byId('deadline').value, priority: byId('priority').value,
      remainingMinutes: Number(byId('remaining').value), done: byId('done').checked
    });
    clearForms(); await refresh(); showMessage('Užduotis išsaugota. Perskaičiuokite grafiką.');
  });
});
byId('availability-form').addEventListener('submit', event => {
  event.preventDefault();
  perform(async () => {
    const id = byId('availability-id').value;
    await api(`/availability${id ? '/' + id : ''}`, id ? 'PUT' : 'POST', {
      start: byId('available-start').value, end: byId('available-end').value
    });
    clearForms(); await refresh(); showMessage('Laisvas laikas išsaugotas. Perskaičiuokite grafiką.');
  });
});
byId('plan-form').addEventListener('submit', event => {
  event.preventDefault();
  perform(async () => {
    const result = await api('/plan', 'POST', { planningStart: byId('planning-start').value, horizonDays: Number(byId('horizon').value) });
    renderSchedule(result); showMessage('Grafikas apskaičiuotas ir išsaugotas.');
  });
});
byId('context').addEventListener('change', () => perform(async () => {
  clearForms();
  try {
    await refresh();
    showMessage('Demo kontekstas pakeistas.');
  } catch (error) {
    // Nepavykus įkelti kito kliento, pasirinkimas turi atitikti teberodomus duomenis.
    byId('context').value = loadedContext;
    throw error;
  }
}));
byId('reset').addEventListener('click', () => perform(async () => {
  await api('/demo/reset', 'POST'); clearForms(); byId('planning-start').value = '2026-10-05T18:00';
  byId('horizon').value = 3; await refresh(); showMessage('Atkurta tik pasirinkto kliento demo įvestis ir apskaičiuotas grafikas.');
}));
byId('cancel-task').addEventListener('click', () => { byId('task-form').reset(); byId('task-id').value = ''; });
byId('cancel-availability').addEventListener('click', () => { byId('availability-form').reset(); byId('availability-id').value = ''; });
perform(refresh);
