defmodule ApiWeb.Resolvers.Domain do
  alias Api.Clients

  defp resolve_word(word, language) do
    %{
      language: language,
      text: Map.get(word, "token"),
      part_of_speech: Map.get(word, "tag"),
      lemma: Map.get(word, "lemma")
    }
  end

  def get_words_in_text(_parent, %{language: language, text: text}, _resolution) do
    case Clients.LanguageService.tag(language, text) do
      {:ok, response} -> {:ok, Enum.map(response, fn word -> resolve_word(word, language) end)}
      _ -> {:error, "Error getting text"}
    end
  end

end