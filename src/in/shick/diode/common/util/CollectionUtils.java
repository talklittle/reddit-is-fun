package in.shick.diode.common.util;

import java.util.Collection;

public class CollectionUtils {

	public static boolean isEmpty(Collection<?> theCollection) {
		return theCollection == null || theCollection.isEmpty();
	}

}
