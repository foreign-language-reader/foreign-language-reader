"""
CEDICT is an open source Chinese dictionary.
This class provides definitions
"""
import json
from language_service.dto.definition import ChineseDefinition


class CEDICT:
    def __init__(self, dictionary_path="content/cedict.json"):
        self.dictionary_path = dictionary_path
        self.definitions = None

    def load_dictionary(self):
        self.definitions = {}
        with open(self.dictionary_path) as cedict:
            for entry in json.load(cedict):
                simplified = entry["simplified"]
                self.definitions[simplified] = entry

    def get_definitions(self, word):
        # Lazy load definitions to make unit testing possible
        if self.definitions is None:
            print("Loading CEDICT dictionary")
            self.load_dictionary()

        print("CEDICT - Getting definitions in CHINESE for %s" % word)

        if word in self.definitions:
            definition = self.definitions[word]
            return ChineseDefinition(
                subdefinitions=definition["definitions"],
                pinyin=definition["pinyin"],
                simplified=definition["simplified"],
                traditional=definition["traditional"],
            )
        else:
            return None