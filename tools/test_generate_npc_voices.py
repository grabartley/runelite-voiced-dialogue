"""Unit tests for the offline NPC voice table generator.

Focused on apply_overrides and its per-field patch semantics: a named field wins, an
omitted field inherits the wiki base, and a null field is cleared, while every entry
must still end up with both race and gender.

Run: python3 -m unittest tools.test_generate_npc_voices  (or python3 tools/test_generate_npc_voices.py)
"""

import json
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


class RaceBucketTest(unittest.TestCase):
    def test_citizen_of_arceuus_buckets_to_its_own_race(self):
        self.assertEqual(gen.bucket_for_race("Citizen of Arceuus"), "Arceuus")
        self.assertEqual(gen.bucket_for_race("Citizens of Arceuus"), "Arceuus")
        self.assertEqual(gen.bucket_for_race("[[Citizen of Arceuus]]"), "Arceuus")

    def test_arceuus_is_matched_before_the_human_fallback(self):
        # "Humans, Dwarves, Citizens of Arceuus" reads as a mixed-population location, but a
        # named Citizen must never fall through to the plain human bucket.
        self.assertEqual(gen.bucket_for_race("Humans, Citizens of Arceuus"), "Arceuus")

    def test_a_mortal_in_arceuus_stays_human(self):
        # Not everyone in Arceuus accepted immortality; the mortals keep the human bucket.
        self.assertEqual(gen.bucket_for_race("Human"), "Human")

    def test_reanimated_arceuus_monsters_are_not_citizens(self):
        self.assertEqual(gen.bucket_for_race("Undead"), "Undead")

    def test_arceuus_category_buckets_when_the_infobox_has_no_race(self):
        self.assertEqual(
            gen.bucket_from_categories(["Category:Citizens of Arceuus"]), "Arceuus"
        )

    def test_arceuus_is_a_valid_override_race(self):
        self.assertIn("Arceuus", gen.VALID_RACES)

    def test_aranei_buckets_to_its_own_race(self):
        self.assertEqual(gen.bucket_for_race("Aranei"), "Aranei")
        self.assertEqual(gen.bucket_for_race("[[Aranei]]"), "Aranei")

    def test_araxytes_are_not_aranei(self):
        # The Nylocas Queen reads as a tribrid including vampyre, so she buckets Undead on the
        # wiki text alone and is pinned away from it in the overrides.
        self.assertEqual(gen.bucket_for_race("Nylocas/araxyte/vampyre tribrid"), "Undead")

    def test_aranei_category_buckets_when_the_infobox_has_no_race(self):
        self.assertEqual(gen.bucket_from_categories(["Category:Aranei"]), "Aranei")

    def test_aranei_is_a_valid_override_race(self):
        self.assertIn("Aranei", gen.VALID_RACES)

    def test_dog_buckets_to_its_own_race(self):
        self.assertEqual(gen.bucket_for_race("Dog"), "Dog")
        self.assertEqual(gen.bucket_for_race("[[Dog]]"), "Dog")

    def test_undead_and_demonic_hounds_keep_their_own_bucket(self):
        self.assertEqual(gen.bucket_for_race("Undead dog"), "Undead")
        self.assertEqual(gen.bucket_for_race("Demonic dog"), "Demon")

    def test_dog_is_a_valid_override_race(self):
        self.assertIn("Dog", gen.VALID_RACES)

    def test_a_hellhound_is_a_demon(self):
        self.assertEqual(gen.bucket_for_race("Hellhound"), "Demon")
        self.assertEqual(gen.bucket_for_race("[[Hellhound]]"), "Demon")

    def test_a_hellhound_dragged_back_from_the_grave_is_undead_first(self):
        for race in ("Skeleton Hellhound", "Revenant hellhound", "Reanimated hellhound"):
            self.assertEqual(gen.bucket_for_race(race), "Undead")

    def test_crabs_and_penguins_bucket_to_their_own_races(self):
        for race in ("Crab", "Crabs", "[[Crab]]", "[[Crab (disambiguation)|Crab]]"):
            self.assertEqual(gen.bucket_for_race(race), "Crab")
        for race in ("Penguin", "Penguins", "[[Penguin]]", "[[Penguin (race)|Penguin]]"):
            self.assertEqual(gen.bucket_for_race(race), "Penguin")

    def test_crabs_and_penguins_are_valid_override_races(self):
        self.assertIn("Crab", gen.VALID_RACES)
        self.assertIn("Penguin", gen.VALID_RACES)

    def test_a_penguin_page_with_no_race_field_buckets_from_its_category(self):
        self.assertEqual(gen.bucket_from_categories(["Category:Penguins"]), "Penguin")

    def test_the_rules_come_from_the_shared_mapping_resource(self):
        with open(gen.MAPPING_PATH, encoding="utf-8") as mapping_file:
            mapping = json.load(mapping_file)
        self.assertEqual(len(gen.RACE_BUCKET_RULES), len(mapping["raceRules"]))
        self.assertEqual(len(gen.CATEGORY_RACE_RULES), len(mapping["categoryRaceRules"]))
        self.assertEqual(gen.SINGLE_ETHNICITY, mapping["leagueRegionEthnicity"])
        self.assertEqual(gen.VALID_RACES, set(mapping["races"]))
        for rules in ("raceRules", "categoryRaceRules"):
            for rule in mapping[rules]:
                self.assertIn(rule["race"], gen.VALID_RACES)
        self.assertEqual(gen.bucket_for_race("nothing the rules know"), mapping["defaultRace"])
        self.assertEqual(gen.normalise_gender(None), mapping["defaultGender"])
        self.assertEqual(gen.normalise_gender("female"), mapping["femaleGender"])

    def test_the_desert_splits_on_categories_as_well_as_location(self):
        self.assertEqual(gen.ethnicity_key("Desert", None, ["Category:Menaphites"]), "menaphite")
        self.assertEqual(gen.ethnicity_key("Desert", None, ["Category:Bandits"]), "kharidian")


class PipedLinkFieldTest(unittest.TestCase):
    def test_a_plain_link_buckets_on_its_page_name(self):
        self.assertEqual(gen.bucket_for_race("[[Dog]]"), "Dog")

    def test_a_piped_link_prefers_its_target(self):
        self.assertEqual(gen.bucket_for_race("[[Dwarf (race)|Dwarves]]"), "Dwarf")

    def test_a_disambiguation_target_falls_through_to_the_display_text(self):
        self.assertEqual(gen.bucket_for_race("[[Dog_(disambiguation)|Dog]]"), "Dog")

    def test_a_section_anchor_target_falls_through_to_the_display_text(self):
        self.assertEqual(
            gen.bucket_for_race("[[Abyss (disambiguation)#Monsters|Abyssal monsters]]"), "Demon")

    def test_neither_side_matching_still_lands_on_the_default(self):
        self.assertEqual(gen.bucket_for_race("[[Nowhere (place)|nobody]]"), gen.MAPPING["defaultRace"])

    def test_a_piped_link_survives_the_field_capture(self):
        wikitext = "{{Infobox NPC\n|race = [[Dog_(disambiguation)|Dog]]\n|gender = Female\n}}"
        self.assertEqual(gen.bucket_for_race(gen.raw_field(wikitext, "race")), "Dog")
        self.assertEqual(gen.parse_genders(wikitext), ["Female"])

    def test_a_single_line_infobox_still_cuts_at_the_next_parameter(self):
        wikitext = "{{Infobox NPC|name=Rosie|race=[[Dog_(disambiguation)|Dog]]|gender=Female|id=10438}}"
        self.assertEqual(gen.bucket_for_race(gen.raw_field(wikitext, "race")), "Dog")
        self.assertEqual(gen.parse_genders(wikitext), ["Female"])
        self.assertEqual(gen.parse_id_groups(wikitext), [[10438]])

    def test_a_piped_league_region_and_location_read_their_targets(self):
        wikitext = ("{{Infobox NPC\n|leagueRegion = [[Kandarin|the Kandarin region]]\n"
                    "|location = [[Ardougne|East Ardougne]]\n}}")
        self.assertEqual(gen.field_value(wikitext, "leagueRegion"), "Kandarin")
        self.assertEqual(gen.field_value(wikitext, "location"), "Ardougne")

    def test_a_templated_field_value_is_not_cut_at_its_inner_pipe(self):
        wikitext = "{{Infobox NPC\n|location = {{plink|Lumbridge}} and [[Draynor|the village]]\n}}"
        self.assertEqual(gen.field_value(wikitext, "location"), "and Draynor")

    def test_switch_infobox_genders_stay_parallel_to_their_id_groups(self):
        wikitext = ("{{Infobox NPC\n|id1 = 10438\n|gender1 = Female\n"
                    "|id2 = 10439\n|gender2 = Female\n}}")
        self.assertEqual(gen.parse_id_groups(wikitext), [[10438], [10439]])
        self.assertEqual(gen.parse_genders(wikitext), ["Female", "Female"])

    def test_a_single_line_switch_infobox_keeps_every_version(self):
        wikitext = ("{{Infobox NPC|id1=10438|gender1=Male|id2=10439|gender2=Female"
                    "|race1=[[Dog]]|race2=[[Dog_(disambiguation)|Dog]]}}")
        self.assertEqual(gen.parse_id_groups(wikitext), [[10438], [10439]])
        self.assertEqual(gen.parse_genders(wikitext), ["Male", "Female"])
        self.assertEqual(gen.bucket_for_race(gen.raw_field(wikitext, "race")), "Dog")

    def test_a_nested_template_value_is_not_cut_at_its_closing_braces(self):
        self.assertEqual(gen.field_text("{{A|{{B|x}}}} tail"), "{{A|{{B|x}}}} tail")

    def test_a_stray_close_ends_the_value_rather_than_swallowing_the_next_parameter(self):
        self.assertEqual(gen.field_text("Dog]] |gender=Female"), "Dog")

    def test_a_race_field_that_cleans_away_falls_back_to_the_categories(self):
        self.assertIsNone(gen.bucket_for_race("{{plink|Human}}"))


if __name__ == "__main__":
    unittest.main()
