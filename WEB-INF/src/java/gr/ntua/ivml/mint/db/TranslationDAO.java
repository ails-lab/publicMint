package gr.ntua.ivml.mint.db;

import java.util.List;

import org.hibernate.criterion.Order;
import org.hibernate.criterion.Restrictions;

import gr.ntua.ivml.mint.persistent.Dataset;
import gr.ntua.ivml.mint.persistent.Organization;
import gr.ntua.ivml.mint.persistent.Translation;

public class TranslationDAO extends DAO<Translation, Long> {

	public List<Translation> findByDataset(Dataset ds) {
		return findByCriteria( Restrictions.eq("originalDataset", ds.getOrigin()));
	}

	public List<Translation> findByOrganization(Organization org) {
		return findByCriteria( Order.desc("startDate") ,Restrictions.eq("organization", org));
	}

	public List<Translation> findByOriginalDataset(Dataset origin) {
		return findByCriteria(Order.desc("startDate") , Restrictions.eq("originalDataset", origin));
	}

}