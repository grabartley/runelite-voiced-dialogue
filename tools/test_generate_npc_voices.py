"""Unit tests for the offline NPC voice table generator.

Focused on apply_overrides, whose per-field patch semantics are the subject of the
override-merge fix: a named field wins, an omitted field inherits the wiki base, and a
null field is cleared, while every entry must still end up with both race and gender.

Run: python3 -m unittest tools.test_generate_npc_voices  (or python3 tools/test_generate_npc_voices.py)
"""

import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import generate_npc_voices as gen  # noqa: E402


def wrap(npcs):
    return {"npcs": npcs}


class ApplyOverridesTest(unittest.TestCase):
    def test_omitted_ethnicity_inherits_wiki_value(self):
        # Karim/Ellis case: an override that only fixes race/gender keeps the wiki accent.
        table = {2877: {"race": "Human", "gender": "Male", "ethnicity": "kharidian"}}
        gen.apply_overrides(table, wrap({"2877": {"race": "Human", "gender": "Male"}}))
        self.assertEqual(table[2877], {"race": "Human", "gender": "Male", "ethnicity": "kharidian"})

    def test_omitted_race_and_gender_inherit_wiki_value(self):
        # An ethnicity-only override no longer has to restate race/gender.
        table = {100: {"race": "Human", "gender": "Female", "ethnicity": "misthalin"}}
        gen.apply_overrides(table, wrap({"100": {"ethnicity": "kandarin"}}))
        self.assertEqual(table[100], {"race": "Human", "gender": "Female", "ethnicity": "kandarin"})

    def test_present_field_wins(self):
        # Ak-Haranu case: an explicit ethnicity overrides the wiki-inferred one.
        table = {2989: {"race": "Human", "gender": "Male", "ethnicity": "kharidian"}}
        gen.apply_overrides(table, wrap({"2989": {"ethnicity": "easternlands"}}))
        self.assertEqual(table[2989]["ethnicity"], "easternlands")

    def test_present_race_and_gender_override_wiki(self):
        table = {5: {"race": "Human", "gender": "Male"}}
        gen.apply_overrides(table, wrap({"5": {"race": "Undead", "gender": "Female"}}))
        self.assertEqual(table[5], {"race": "Undead", "gender": "Female"})

    def test_null_ethnicity_clears_wiki_value(self):
        # A foreigner found in Morytania: null drops the wrong wiki accent -> British default.
        table = {200: {"race": "Human", "gender": "Male", "ethnicity": "morytania"}}
        gen.apply_overrides(table, wrap({"200": {"ethnicity": None}}))
        self.assertEqual(table[200], {"race": "Human", "gender": "Male"})
        self.assertNotIn("ethnicity", table[200])

    def test_null_ethnicity_is_noop_when_absent(self):
        table = {201: {"race": "Human", "gender": "Male"}}
        gen.apply_overrides(table, wrap({"201": {"ethnicity": None}}))
        self.assertEqual(table[201], {"race": "Human", "gender": "Male"})

    def test_override_for_unknown_id_supplies_full_entry(self):
        table = {}
        gen.apply_overrides(table, wrap({"9": {"race": "Goblin", "gender": "Male"}}))
        self.assertEqual(table[9], {"race": "Goblin", "gender": "Male"})

    def test_missing_race_with_no_wiki_base_raises_naming_id(self):
        table = {}
        with self.assertRaises(ValueError) as ctx:
            gen.apply_overrides(table, wrap({"42": {"gender": "Male"}}))
        self.assertIn("42", str(ctx.exception))
        self.assertIn("race", str(ctx.exception))

    def test_missing_gender_with_no_wiki_base_raises_naming_id(self):
        table = {}
        with self.assertRaises(ValueError) as ctx:
            gen.apply_overrides(table, wrap({"43": {"race": "Human"}}))
        self.assertIn("43", str(ctx.exception))
        self.assertIn("gender", str(ctx.exception))

    def test_null_race_clearing_wiki_base_raises_invariant(self):
        table = {7: {"race": "Human", "gender": "Male"}}
        with self.assertRaises(ValueError) as ctx:
            gen.apply_overrides(table, wrap({"7": {"race": None}}))
        self.assertIn("7", str(ctx.exception))

    def test_invalid_race_raises(self):
        table = {}
        with self.assertRaises(ValueError):
            gen.apply_overrides(table, wrap({"1": {"race": "Orc", "gender": "Male"}}))

    def test_invalid_gender_raises(self):
        table = {}
        with self.assertRaises(ValueError):
            gen.apply_overrides(table, wrap({"1": {"race": "Human", "gender": "Other"}}))

    def test_name_field_is_ignored(self):
        table = {8: {"race": "Human", "gender": "Male", "ethnicity": "asgarnia"}}
        gen.apply_overrides(table, wrap({"8": {"name": "Bob", "gender": "Female"}}))
        self.assertEqual(table[8], {"race": "Human", "gender": "Female", "ethnicity": "asgarnia"})

    def test_patch_does_not_corrupt_sibling_sharing_wiki_dict(self):
        # Wiki entries are shared across sibling ids; patching one must not mutate the other.
        shared = {"race": "Human", "gender": "Male", "ethnicity": "kandarin"}
        table = {10: shared, 11: shared}
        gen.apply_overrides(table, wrap({"10": {"ethnicity": None}}))
        self.assertNotIn("ethnicity", table[10])
        self.assertEqual(table[11]["ethnicity"], "kandarin")

    def test_return_value_counts_overrides(self):
        table = {1: {"race": "Human", "gender": "Male"}}
        count = gen.apply_overrides(table, wrap({"1": {"gender": "Female"}, "2": {"race": "Human", "gender": "Male"}}))
        self.assertEqual(count, 2)


if __name__ == "__main__":
    unittest.main()
