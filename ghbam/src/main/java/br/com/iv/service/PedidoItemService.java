package br.com.iv.service;

import br.com.iv.repository.PedidoItemRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class PedidoItemService {


    @Inject
    PedidoItemRepository itemRepository;

   
    
    @Transactional
    public void delete(Long id) {
    	itemRepository.deleteById(id);
    }

    
}
