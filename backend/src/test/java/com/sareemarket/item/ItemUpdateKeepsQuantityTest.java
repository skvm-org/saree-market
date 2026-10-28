package com.sareemarket.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.sareemarket.IntegrationTestBase;

/** A seller editing one field must not write back a stale stock count over a checkout that just happened. */
class ItemUpdateKeepsQuantityTest extends IntegrationTestBase {

	@Autowired
	ItemRepository items;

	@Autowired
	TransactionTemplate tx;

	@Test
	void editingColourDoesNotOverwriteQuantityChangedMeanwhile() throws Exception {
		long seller = userId(registerSeller());
		long id = insertItem(insertCollection(seller, 1000), seller);
		jdbc.update("UPDATE item SET quantity = 5 WHERE id = ?", id);

		tx.executeWithoutResult(status -> {
			Item item = items.findById(id).orElseThrow();
			jdbc.update("UPDATE item SET quantity = 3 WHERE id = ?", id); // a checkout commits in between
			item.setColour("peacock blue");
		});

		assertThat(jdbc.queryForObject("SELECT quantity FROM item WHERE id = ?", Integer.class, id)).isEqualTo(3);
		assertThat(jdbc.queryForObject("SELECT colour FROM item WHERE id = ?", String.class, id)).isEqualTo("peacock blue");
	}

}
