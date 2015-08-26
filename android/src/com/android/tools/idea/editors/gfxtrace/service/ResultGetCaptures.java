/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * THIS FILE WAS GENERATED BY codergen. EDIT WITH CARE.
 */
package com.android.tools.idea.editors.gfxtrace.service;

import com.android.tools.idea.editors.gfxtrace.service.path.CapturePath;
import com.android.tools.rpclib.binary.*;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

final class ResultGetCaptures implements BinaryObject {
  //<<<Start:Java.ClassBody:1>>>
  private CapturePath[] myValue;

  // Constructs a default-initialized {@link ResultGetCaptures}.
  public ResultGetCaptures() {}


  public CapturePath[] getValue() {
    return myValue;
  }

  public ResultGetCaptures setValue(CapturePath[] v) {
    myValue = v;
    return this;
  }

  @Override @NotNull
  public BinaryClass klass() { return Klass.INSTANCE; }

  private static final byte[] IDBytes = {20, -106, 6, 47, -126, 24, 19, -16, -109, 39, 99, -127, 127, -117, 53, -60, 55, 47, 118, 78, };
  public static final BinaryID ID = new BinaryID(IDBytes);

  static {
    Namespace.register(ID, Klass.INSTANCE);
  }
  public static void register() {}
  //<<<End:Java.ClassBody:1>>>
  public enum Klass implements BinaryClass {
    //<<<Start:Java.KlassBody:2>>>
    INSTANCE;

    @Override @NotNull
    public BinaryID id() { return ID; }

    @Override @NotNull
    public BinaryObject create() { return new ResultGetCaptures(); }

    @Override
    public void encode(@NotNull Encoder e, BinaryObject obj) throws IOException {
      ResultGetCaptures o = (ResultGetCaptures)obj;
      e.uint32(o.myValue.length);
      for (int i = 0; i < o.myValue.length; i++) {
        e.object(o.myValue[i]);
      }
    }

    @Override
    public void decode(@NotNull Decoder d, BinaryObject obj) throws IOException {
      ResultGetCaptures o = (ResultGetCaptures)obj;
      o.myValue = new CapturePath[d.uint32()];
      for (int i = 0; i <o.myValue.length; i++) {
        o.myValue[i] = (CapturePath)d.object();
      }
    }
    //<<<End:Java.KlassBody:2>>>
  }
}
