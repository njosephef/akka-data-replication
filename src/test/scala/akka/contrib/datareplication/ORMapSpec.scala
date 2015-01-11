/**
 * Copyright (C) 2009-2014 Typesafe Inc. <http://www.typesafe.com>
 */

package akka.contrib.datareplication

import org.scalatest.WordSpec
import org.scalatest.Matchers
import akka.actor.Address
import akka.cluster.UniqueAddress

class ORMapSpec extends WordSpec with Matchers {

  val node1 = UniqueAddress(Address("akka.tcp", "Sys", "localhost", 2551), 1)
  val node2 = UniqueAddress(node1.address.copy(port = Some(2552)), 2)

  "A ORMap" must {

    "be able to add entries" in {
      val m = ORMap().put(node1, "a", GSet() + "A").put(node1, "b", GSet() + "B")
      val GSet(a) = m.entries("a")
      a should be(Set("A"))
      val GSet(b) = m.entries("b")
      b should be(Set("B"))

      val m2 = m.put(node1, "a", GSet() + "C")
      val GSet(a2) = m2.entries("a")
      a2 should be(Set("C"))

    }

    "be able to remove entry" in {
      val m = ORMap().put(node1, "a", GSet() + "A").put(node1, "b", GSet() + "B").remove(node1, "a")
      m.entries.keySet should not contain ("a")
      m.entries.keySet should contain("b")
    }

    "be able to add removed" in {
      val m = ORMap().put(node1, "a", GSet() + "A").put(node1, "b", GSet() + "B").remove(node1, "a")
      m.entries.keySet should not contain ("a")
      m.entries.keySet should contain("b")
      val m2 = m.put(node1, "a", GSet() + "C")
      m2.entries.keySet should contain("a")
      m2.entries.keySet should contain("b")
    }

    "be able to have its entries correctly merged with another ORMap with other entries" in {
      val m1 = ORMap().put(node1, "a", GSet() + "A").put(node1, "b", GSet() + "B")
      val m2 = ORMap().put(node2, "c", GSet() + "C")

      // merge both ways
      val merged1 = m1 merge m2
      merged1.entries.keySet should contain("a")
      merged1.entries.keySet should contain("b")
      merged1.entries.keySet should contain("c")

      val merged2 = m2 merge m1
      merged2.entries.keySet should contain("a")
      merged2.entries.keySet should contain("b")
      merged2.entries.keySet should contain("c")
    }

    "be able to have its entries correctly merged with another ORMap with overlapping entries" in {
      val m1 = ORMap().put(node1, "a", GSet() + "A1").put(node1, "b", GSet() + "B1").
        remove(node1, "a").put(node1, "d", GSet() + "D1")
      val m2 = ORMap().put(node2, "c", GSet() + "C2").put(node2, "a", GSet() + "A2").
        put(node2, "b", GSet() + "B2").remove(node2, "b").put(node2, "d", GSet() + "D2")

      // merge both ways
      val merged1 = m1 merge m2
      merged1.entries.keySet should contain("a")
      val GSet(a1) = merged1.entries("a")
      a1 should be(Set("A2"))
      merged1.entries.keySet should contain("b")
      val GSet(b1) = merged1.entries("b")
      b1 should be(Set("B1"))
      merged1.entries.keySet should contain("c")
      merged1.entries.keySet should contain("d")
      val GSet(d1) = merged1.entries("d")
      d1 should be(Set("D1", "D2"))

      val merged2 = m2 merge m1
      merged2.entries.keySet should contain("a")
      val GSet(a2) = merged1.entries("a")
      a2 should be(Set("A2"))
      merged2.entries.keySet should contain("b")
      val GSet(b2) = merged2.entries("b")
      b2 should be(Set("B1"))
      merged2.entries.keySet should contain("c")
      merged2.entries.keySet should contain("d")
      val GSet(d2) = merged2.entries("d")
      d2 should be(Set("D1", "D2"))
    }

    "illustrate the danger of using remove+put to replace an entry" in {
      val m1 = ORMap().put(node1, "a", GSet() + "A").put(node1, "b", GSet() + "B")
      val m2 = ORMap().put(node2, "c", GSet() + "C")

      val merged1 = m1 merge m2

      val m3 = merged1.remove(node1, "b").put(node1, "b", GSet() + "B2")
      // same thing if only put is used
      // val m3 = merged1.put(node1, "b", GSet() + "B2")
      val merged2 = merged1 merge m3

      merged2.entries("a").asInstanceOf[GSet].value should be(Set("A"))
      // note that B is included, because GSet("B") is merged with GSet("B2")
      merged2.entries("b").asInstanceOf[GSet].value should be(Set("B", "B2"))
      merged2.entries("c").asInstanceOf[GSet].value should be(Set("C"))
    }

    "not allow put for ORSet value type" in {
      val m = ORMap().put(node1, "a", ORSet().add(node1, "A"))

      intercept[IllegalArgumentException] {
        m.put(node1, "a", ORSet().add(node1, "B"))
      }
    }

    "be able to update entry" in {
      val m1 = ORMap().put(node1, "a", ORSet().add(node1, "A")).put(node1, "b", ORSet().add(node1, "B"))
      val m2 = ORMap().put(node2, "c", ORSet().add(node2, "C"))

      val merged1 = m1 merge m2

      // note that remove + put doesn't work, because the new ORSet in the put doesn't have
      // the history needed for proper merging
      // val m3 = merged1.remove(node1, "b").put(node1, "b", ORSet().add(node1, "B2"))
      val m3 = merged1.updated(node1, "b", ORSet())(_.clear(node1).add(node1, "B2"))

      val merged2 = merged1 merge m3
      merged2.entries("a").asInstanceOf[ORSet].value should be(Set("A"))
      merged2.entries("b").asInstanceOf[ORSet].value should be(Set("B2"))
      merged2.entries("c").asInstanceOf[ORSet].value should be(Set("C"))

      val m4 = merged1.updated(node2, "b", ORSet())(_.add(node2, "B3"))
      val merged3 = m3 merge m4
      merged3.entries("a").asInstanceOf[ORSet].value should be(Set("A"))
      merged3.entries("b").asInstanceOf[ORSet].value should be(Set("B2", "B3"))
      merged3.entries("c").asInstanceOf[ORSet].value should be(Set("C"))
    }

  }
}
