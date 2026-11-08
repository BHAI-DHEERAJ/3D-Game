const $ = (id) => document.getElementById(id);

export class HUD {
  constructor() {
    this.el = {
      hud: $('hud'),
      phaseName: $('phaseName'),
      timer: $('timer'),
      propScore: $('propScore'),
      hunterScore: $('hunterScore'),
      hpFill: $('hpFill'),
      hpText: $('hpText'),
      roleTag: $('roleTag'),
      ammo: $('ammo'),
      reloadHint: $('reloadHint'),
      prompt: $('prompt'),
      feed: $('feed'),
      killfeed: $('killfeed'),
      hitmarker: $('hitmarker'),
      damage: $('damage'),
      musicWarn: $('musicWarn'),
      scoreboard: $('scoreboard'),
      scoreBody: $('scoreBody'),
      death: $('deathOverlay'),
      deathText: $('deathText'),
      abPrimary: $('abPrimary'),
      abSecondary: $('abSecondary'),
      crosshair: $('crosshair'),
      ping: $('ping'),
    };
    this.lastHit = 0;
  }

  show(v) { this.el.hud.classList.toggle('hidden', !v); }

  setPhase(name, seconds) {
    this.el.phaseName.textContent = name;
    const s = Math.max(0, Math.ceil(seconds));
    this.el.timer.textContent = `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;
    this.el.timer.classList.toggle('urgent', s <= 10);
  }

  setScores(p, h) {
    this.el.propScore.textContent = Math.round(p);
    this.el.hunterScore.textContent = Math.round(h);
  }

  setHealth(hp, max) {
    const pct = Math.max(0, Math.min(100, (hp / max) * 100));
    this.el.hpFill.style.width = `${pct}%`;
    this.el.hpFill.style.background = pct > 55 ? '#5fd08a' : pct > 25 ? '#e8c04a' : '#e05a4a';
    this.el.hpText.textContent = Math.max(0, Math.ceil(hp));
  }

  setAmmo(ammo, max, reloading) {
    this.el.ammo.innerHTML = reloading
      ? `<span class="rel">Reloading…</span>`
      : `${ammo}<span class="mag"> / ${max}</span>`;
    this.el.reloadHint.classList.toggle('hidden', !(ammo === 0 && !reloading));
    this.el.ammo.classList.toggle('empty', ammo === 0 && !reloading);
  }

  setRole(team, propType) {
    const el = this.el.roleTag;
    el.textContent = team === 'hunter' ? 'HUNTER' : (propType ? propType.toUpperCase() : 'PROP (no disguise!)');
    el.className = team === 'hunter' ? 'hunter' : (propType ? 'prop' : 'prop exposed');
    this.el.ammo.parentElement.style.display = team === 'hunter' ? '' : 'none';
  }

  setPrompt(text) {
    this.el.prompt.innerHTML = text || '';
    this.el.prompt.classList.toggle('hidden', !text);
  }

  setAbilities({ grenadeReady, grenadeCd, medkits, isHunter }) {
    const p = this.el.abPrimary;
    const s = this.el.abSecondary;
    if (isHunter) {
      p.style.display = '';
      p.querySelector('.nm').textContent = 'Grenade';
      p.querySelector('.key').textContent = 'G';
      p.classList.toggle('cooling', !grenadeReady);
      p.querySelector('.cd').textContent = grenadeReady ? '' : `${grenadeCd.toFixed(1)}s`;
    } else {
      p.style.display = 'none';
    }
    s.style.display = isHunter ? 'none' : '';
    s.querySelector('.nm').textContent = 'Medkit';
    s.querySelector('.cd').textContent = medkits > 0 ? `x${medkits}` : 'empty';
    s.classList.toggle('cooling', medkits === 0);
  }

  addFeed(text) {
    const d = document.createElement('div');
    d.className = 'feedLine';
    d.textContent = text;
    this.el.feed.appendChild(d);
    setTimeout(() => d.remove(), 6000);
    while (this.el.feed.children.length > 5) this.el.feed.firstChild.remove();
  }

  hitmarker(kill = false) {
    const hm = this.el.hitmarker;
    hm.textContent = kill ? '✖' : '✕';
    hm.classList.toggle('kill', kill);
    hm.classList.remove('show');
    void hm.offsetWidth;
    hm.classList.add('show');
  }

  damageFlash() {
    const d = this.el.damage;
    d.classList.remove('show');
    void d.offsetWidth;
    d.classList.add('show');
  }

  showMusic(v) { this.el.musicWarn.classList.toggle('hidden', !v); }

  setScoreboard(rows, visible) {
    this.el.scoreboard.classList.toggle('hidden', !visible);
    if (!visible) return;
    this.el.scoreBody.innerHTML = rows.map((r, i) => `
      <tr class="${r.me ? 'me' : ''}">
        <td>${i + 1}</td>
        <td>${escapeHtml(r.name)}${r.bot ? ' <span class="bot">BOT</span>' : ''}</td>
        <td class="${r.team}">${r.team === 'hunter' ? 'Hunter' : 'Prop'}</td>
        <td>${r.alive ? 'alive' : 'down'}</td>
        <td>${r.kills}</td>
        <td><b>${r.score}</b></td>
      </tr>`).join('');
  }

  setDeath(info) {
    if (!info) { this.el.death.classList.add('hidden'); return; }
    this.el.death.classList.remove('hidden');
    this.el.deathText.innerHTML = info;
  }

  setPing(ms) { if (this.el.ping) this.el.ping.textContent = `${ms}ms`; }
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => (
    { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
  ));
}
