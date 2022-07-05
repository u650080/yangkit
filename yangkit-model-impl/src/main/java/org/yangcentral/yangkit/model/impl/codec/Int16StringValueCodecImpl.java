package org.yangcentral.yangkit.model.impl.codec;

import org.yangcentral.yangkit.base.ErrorCode;
import org.yangcentral.yangkit.model.api.codec.Int16StringValueCodec;
import org.yangcentral.yangkit.model.api.codec.YangCodecException;
import org.yangcentral.yangkit.model.api.restriction.Restriction;

public class Int16StringValueCodecImpl extends StringValueCodecImpl<Short> implements Int16StringValueCodec {
   public Short deserialize(Restriction<Short> restriction, String input) throws YangCodecException {
      Short s = Short.valueOf(input);
      if (!restriction.evaluated(s)) {
         throw new YangCodecException(ErrorCode.INVALID_VALUE.getFieldName());
      } else {
         return s;
      }
   }

   public String serialize(Restriction<Short> restriction, Short output) throws YangCodecException {
      if (!restriction.evaluated(output)) {
         throw new YangCodecException(ErrorCode.INVALID_VALUE.getFieldName());
      } else {
         return output.toString();
      }
   }
}
