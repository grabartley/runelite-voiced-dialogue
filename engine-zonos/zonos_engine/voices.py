"""Maps a plugin ``voice{player, race, gender}`` request onto a Zonos reference-voice id.

Must mirror the plugin's ``ZonosVoiceMap`` exactly (same ids, slots, default, and "unknown gender ->
male slot" rule) so neither side invents an id the other does not expect.
"""

from __future__ import annotations

import os
from typing import Optional

DEFAULT_VOICE = "narrator_neutral"

_NPC_VOICES = {
    "HUMAN": ("human_male", "human_female"),
    "ELF": ("elf_male", "elf_female"),
    "DWARF": ("dwarf_male", "dwarf_female"),
    "GOBLIN": ("goblin_male", "goblin_female"),
    "TROLL": ("troll_male", "troll_female"),
    "UNDEAD": ("undead_male", "undead_female"),
    "DEMON": ("demon_male", "demon_female"),
    "WIZARD": ("wizard_male", "wizard_female"),
}

_PLAYER_VOICES = {"MALE": "player_male", "FEMALE": "player_female"}


def voice_for(player: bool, race: Optional[str], gender: Optional[str]) -> str:
    g = _normalize_gender(gender)
    if player:
        return _PLAYER_VOICES.get(g, DEFAULT_VOICE)
    slots = _NPC_VOICES.get(race.upper() if isinstance(race, str) else "")
    if slots is None:
        return DEFAULT_VOICE
    male, female = slots
    return female if g == "FEMALE" else male


def _normalize_gender(gender: Optional[str]) -> str:
    return "FEMALE" if isinstance(gender, str) and gender.upper() == "FEMALE" else "MALE"


def all_voice_ids():
    ids = {DEFAULT_VOICE}
    ids.update(_PLAYER_VOICES.values())
    for male, female in _NPC_VOICES.values():
        ids.add(male)
        ids.add(female)
    return sorted(ids)


def voices_dir(bundle_root: str) -> str:
    return os.path.join(bundle_root, "voices")


def embedding_path_for(bundle_root: str, voice_id: str) -> str:
    return os.path.join(voices_dir(bundle_root), voice_id + ".wav")
