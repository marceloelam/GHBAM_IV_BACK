package br.com.iv.util;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AbreviarNome implements Serializable {
	private static final long serialVersionUID = 1L;

	public static String abreviar(String nomeDaPessoa) {
		nomeDaPessoa = nomeDaPessoa.trim();
	    if (nomeDaPessoa.length() > 10) {
	      List<String> preposicoes = List.of("DE", "DA", "DO", "DAS", "DOS");
	      String[] nomes = nomeDaPessoa.toUpperCase().split(" ");
	      return nomes[0]
	          + " "
	          + Arrays.stream(nomes)
	              .limit(nomes.length - 1)
	              .skip(1)
	              .filter(nome -> !preposicoes.contains(nome))
	              .map(nome -> nome.substring(0, 1) + ". ")
	              .collect(Collectors.joining())
	          + nomes[nomes.length - 1];
	    }

	    return nomeDaPessoa;
	  }
}
