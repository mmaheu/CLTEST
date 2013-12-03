<?php
/**
 * @package     Joomla.UnitTest
 * @subpackage  Pagination
 *
 * @copyright   Copyright (C) 2005 - 2013 Open Source Matters, Inc. All rights reserved.
 * @license     GNU General Public License version 2 or later; see LICENSE
 */

/**
 * Test class for JPagination.
 *
 * @package     Joomla.UnitTest
 * @subpackage  Pagination
 * @since       3.1
 */
class JPaginationTest extends TestCase
{
	/**
	 * Setup for testing.
	 *
	 * @return  void
	 *
	 * @since   3.1
	 */
	public function setUp()
	{
		parent::setUp();

		// We are only coupled to Application and Language in JFactory.
		$this->saveFactoryState();

		JFactory::$language = $this->getMockLanguage();
		JFactory::$application = $this->getMockApplication();
	}

	/**
	 * Overrides the parent tearDown method.
	 *
	 * @return  void
	 *
	 * @see     PHPUnit_Framework_TestCase::tearDown()
	 * @since   3.1
	 */
	protected function tearDown()
	{
		$this->restoreFactoryState();

		parent::tearDown();
	}

	/**
	 * Provides the data to test the constructor method.
	 *
	 * @return  array
	 *
	 * @since   3.1
	 */
	public function dataTestConstructor()
	{
		return array(
			array(100, 0, 20,
				array(
					'total' => 100,
					'limitstart' => 0,
					'limit' => 20,
					'pages.total' => 5,
					'pages.current' => 1,
					'pages.start' => 1,
					'pages.stop' => 5
				)
			),

			array(100, 101, 20,
				array(
					'total' => 100,
					'limitstart' => 80,
					'limit' => 20,
					'pages.total' => 5,
					'pages.current' => 5,
					'pages.start' => 1,
					'pages.stop' => 5
				)
			),

			array(100, 201, 20,
				array(
					'total' => 100,
					'limitstart' => 80,
					'limit' => 20,
					'pages.total' => 5,
					'pages.current' => 5,
					'pages.start' => 1,
					'pages.stop' => 5
				)
			),

			array(10, 20, 20,
				array(
					'total' => 10,
					'limitstart' => 0,
					'limit' => 20,
					'pages.total' => 1,
					'pages.current' => 1,
					'pages.start' => 1,
					'pages.stop' => 1
				)
			),

			array(10, 0, null,
				array(
					'total' => 10,
					'limitstart' => 0,
					'limit' => 10,
					'pages.total' => 1,
					'pages.current' => 1,
					'pages.start' => 1,
					'pages.stop' => 1
				)
			),
		);
	}

	/**
	 * This method tests the constructor.
	 *
	 * This is a basic data driven test. It takes the data passed, runs the constructor
	 * and make sure the appropriate values get setup.
	 *
	 * @param   integer  $total       The total number of items.
	 * @param   integer  $limitstart  The offset of the item to start at.
	 * @param   integer  $limit       The number of items to display per page.
	 * @param   array    $expected    The expected results for the JPagination object
	 *
	 * @return  void
	 *
	 * @covers        JPagination::__construct
	 * @dataProvider  dataTestConstructor
	 * @since         3.1
	 */
	public function testConstructor($total, $limitstart, $limit, $expected)
	{
		$pagination = new JPagination($total, $limitstart, $limit);

		$this->assertEquals($expected['total'], $pagination->total, 'Wrong Total');

		$this->assertEquals($expected['limitstart'], $pagination->limitstart, 'Wrong Limitstart');

		$this->assertEquals($expected['limit'], $pagination->limit, 'Wrong Limit');

		$this->assertEquals($expected['pages.total'], $pagination->pagesTotal, 'Wrong Total Pages');

		$this->assertEquals($expected['pages.current'], $pagination->pagesCurrent, 'Wrong Current Page');

		$this->assertEquals($expected['pages.start'], $pagination->pagesStart, 'Wrong Start Page');

		$this->assertEquals($expected['pages.stop'], $pagination->pagesStop, 'Wrong Stop Page');

		unset($pagination);
	}

	/**
	 * This method tests the setAdditionalUrlParam function by setting a url.
	 *
	 * @return  void
	 *
	 * @covers        JPagination::setAdditionalUrlParam
	 * @since         3.1
	 */
	public function testSetAdditionalUrlParam()
	{
		$pagination = new JPagination(100, 50, 20);

		$pagination->setAdditionalUrlParam('Joomla', '//www.joomla.org');
		$this->assertEquals(TestReflection::getValue($pagination, 'additionalUrlParams'), array('Joomla' => '//www.joomla.org'), 'The URL is not the value expected');

		unset($pagination);
	}

	/**
	 * This method tests the getAdditionalUrlParam function by setting a url with Reflection then retrieving it.
	 *
	 * @return  void
	 *
	 * @covers        JPagination::getAdditionalUrlParam
	 * @since         3.1
	 */
	public function testGetAdditionalUrlParam()
	{
		$pagination = new JPagination(100, 50, 20);
		$value = '//www.joomla.org';
		$key = 'Joomla';

		TestReflection::setValue($pagination, 'additionalUrlParams', array($key => $value));

		$this->assertEquals($value, $pagination->getAdditionalUrlParam($key), 'The URL is not the value expected');

		unset($pagination);
	}

	/**
	 * This method tests the getRowOffset function.
	 *
	 * @param   integer  $index       The row index
	 * @param   integer  $limitstart  The offset of the item to start at.
	 * @param   integer  $value       The expected rationalised offset for a row with a given index.
	 *
	 * @return  void
	 *
	 * @covers        JPagination::getRowOffset
	 * @since         3.1
	 */
	public function testGetRowOffset($index = 1, $limitstart = 50, $value = 52)
	{
		$pagination = new JPagination(100, $limitstart, 20);

		$this->assertEquals($pagination->getRowOffset($index), $value);

		unset($pagination);
	}

	/**
	 * This method tests the setAdditionalUrlParam function by emptying an existing URL.
	 *
	 * @return  void
	 *
	 * @covers        JPagination::setAdditionalUrlParam
	 * @since         3.1
	 */
	public function testSetEmptyAdditionalUrlParam()
	{
		$pagination = new JPagination(100, 50, 20);

		$pagination->setAdditionalUrlParam('Joomla', '//www.joomla.org');
		$this->assertEquals(TestReflection::getValue($pagination, 'additionalUrlParams'), array('Joomla' => '//www.joomla.org'), 'The URL is not the value expected');

		$pagination->setAdditionalUrlParam('Joomla', null);
		$this->assertArrayNotHasKey('Joomla', TestReflection::getValue($pagination, 'additionalUrlParams'));

		unset($pagination);
	}

	/**
	 * Provides the data to test the testBuildDataObject and getData methods.
	 *
	 * @return  array
	 *
	 * @since   3.2
	 */
	public function dataTestBuildDataObject()
	{
		return array(
			array(100, 40, 20, 3,
				array(
					array(
						'text' => 'JLIB_HTML_VIEW_ALL',
						'base' => '0',
						'link' => '',
						'prefix' => '',
						'active' => '',
					),
					array(
						'text' => 'JLIB_HTML_START',
						'base' => '0',
						'link' => '',
						'prefix' => '',
						'active' => '',
					),
					array(
						'text' => 'JPREV',
						'base' => '20',
						'link' => '',
						'prefix' => '',
						'active' => '',
					),
					array(
						'text' => 'JNEXT',
						'base' => '60',
						'link' => '',
						'prefix' => '',
						'active' => '',
					),
					array(
						'text' => 'JLIB_HTML_END',
						'base' => '80',
						'link' => '',
						'prefix' => '',
						'active' => '',
					),
					array(
						'text' => '3',
						'base' => '',
						'link' => '',
						'prefix' => '',
						'active' => true,
					),
				)
			),
		);
	}

	/**
	 * This method tests the _buildDataObject method.
	 *
	 * @param   integer  $total       The total number of items.
	 * @param   integer  $limitstart  The offset of the item to start at.
	 * @param   integer  $limit       The number of items to display per page.
	 * @param   integer  $active      The page number which contains the active pagination.
	 * @param   array    $expected    The expected results for the JPagination object
	 *
	 * @return  void
	 *
	 * @dataProvider  dataTestBuildDataObject
	 * @covers        JPagination::getData
	 * @since         3.2
	 */
	public function testGetData($total, $limitstart, $limit, $active, $expected)
	{
		$pagination = new JPagination($total, $limitstart, $limit);

		$object = $pagination->getData();

		// Test the view all Object
		$this->assertEquals($object->all->text, $expected["0"]["text"], 'This is not the expected view all text');
		$this->assertEquals($object->all->base, $expected["0"]["base"], 'This is not the expected view all base value');
		$this->assertEquals($object->all->link, $expected["0"]["link"], 'This is not the expected view all link value');
		$this->assertEquals($object->all->prefix, $expected["0"]["prefix"], 'This is not the expected view all prefix value');
		$this->assertEquals($object->all->active, $expected["0"]["active"], 'This is not the expected view all active value');

		// Test the start Object
		$this->assertEquals($object->start->text, $expected["1"]["text"], 'This is not the expected start text');
		$this->assertEquals($object->start->base, $expected["1"]["base"], 'This is not the expected start base value');
		$this->assertEquals($object->start->link, $expected["1"]["link"], 'This is not the expected start link value');
		$this->assertEquals($object->start->prefix, $expected["1"]["prefix"], 'This is not the expected start prefix value');
		$this->assertEquals($object->start->active, $expected["1"]["active"], 'This is not the expected start active value');

		// Test the previous Object
		$this->assertEquals($object->previous->text, $expected["2"]["text"], 'This is not the expected previous text');
		$this->assertEquals($object->previous->base, $expected["2"]["base"], 'This is not the expected previous base value');
		$this->assertEquals($object->previous->link, $expected["2"]["link"], 'This is not the expected previous link value');
		$this->assertEquals($object->previous->prefix, $expected["2"]["prefix"], 'This is not the expected previous prefix value');
		$this->assertEquals($object->previous->active, $expected["2"]["active"], 'This is not the expected previous active value');

		// Test the next Object
		$this->assertEquals($object->next->text, $expected["3"]["text"], 'This is not the expected next text');
		$this->assertEquals($object->next->base, $expected["3"]["base"], 'This is not the expected next base value');
		$this->assertEquals($object->next->link, $expected["3"]["link"], 'This is not the expected next link value');
		$this->assertEquals($object->next->prefix, $expected["3"]["prefix"], 'This is not the expected next prefix value');
		$this->assertEquals($object->next->active, $expected["3"]["active"], 'This is not the expected next active value');

		// Test the end Object
		$this->assertEquals($object->end->text, $expected["4"]["text"], 'This is not the expected end text');
		$this->assertEquals($object->end->base, $expected["4"]["base"], 'This is not the expected end base value');
		$this->assertEquals($object->end->link, $expected["4"]["link"], 'This is not the expected end link value');
		$this->assertEquals($object->end->prefix, $expected["4"]["prefix"], 'This is not the expected end prefix value');
		$this->assertEquals($object->end->active, $expected["4"]["active"], 'This is not the expected end active value');

		// Test the active object
		$this->assertEquals($object->pages[$active]->text, $expected["5"]["text"], 'This is not the expected active text');
		$this->assertEquals($object->pages[$active]->base, $expected["5"]["base"], 'This is not the expected active base value');
		$this->assertEquals($object->pages[$active]->link, $expected["5"]["link"], 'This is not the expected active link value');
		$this->assertEquals($object->pages[$active]->prefix, $expected["5"]["prefix"], 'This is not the expected active prefix value');
		$this->assertEquals($object->pages[$active]->active, $expected["5"]["active"], 'This is not the expected active active value');

		unset($pagination);
	}

	/**
	 * Provides the data to test the getPagesCounter() method.
	 *
	 * @return  array
	 *
	 * @since   3.2
	 */
	public function dataTestGetPagesCounter()
	{
		return array(
			array(100, 50, 20, 'JLIB_HTML_PAGE_CURRENT_OF_TOTAL'),
			array(20, 50, 0, ''),
		);
	}

	/**
	 * Tests the getPagesCounter method.
	 *
	 * @param   integer  $total       The total number of items.
	 * @param   integer  $limitstart  The offset of the item to start at.
	 * @param   integer  $limit       The number of items to display per page.
	 * @param   array    $expected    The expected results for the JPagination object
	 *
	 * @return  void
	 *
	 * @covers        JPagination::getPagesCounter
	 * @dataProvider  dataTestGetPagesCounter

	 * @since  3.2
	 */
	public function testGetPagesCounter($total, $limitstart, $limit, $expected)
	{
		$pagination = new JPagination($total, $limitstart, $limit);

		$this->assertEquals($pagination->getPagesCounter(), $expected);

		unset($pagination);
	}

	/**
	 * Provides the data to test the getPagesLinks method.
	 *
	 * @return  array
	 *
	 * @since   3.2
	 */
	public function dataTestGetPagesLinks()
	{
		return array(
			array(100, 50, 20, '<ul><li class="pagination-start"><a title="JLIB_HTML_START" href="" class="hasTooltip pagenav">JLIB_HTML_START</a></li><li class="pagination-prev"><a title="JPREV" href="" class="hasTooltip pagenav">JPREV</a></li><li><a href="" class="pagenav">1</a></li><li><a href="" class="pagenav">2</a></li><li><span class="pagenav">3</span></li><li><a href="" class="pagenav">4</a></li><li><a href="" class="pagenav">5</a></li><li class="pagination-next"><a title="JNEXT" href="" class="hasTooltip pagenav">JNEXT</a></li><li class="pagination-end"><a title="JLIB_HTML_END" href="" class="hasTooltip pagenav">JLIB_HTML_END</a></li></ul>'),
		);
	}

	/**
	 * This method tests the getPagesLinks method.
	 *
	 * @param   integer  $total       The total number of items.
	 * @param   integer  $limitstart  The offset of the item to start at.
	 * @param   integer  $limit       The number of items to display per page.
	 * @param   array    $expected    The expected results for the JPagination object
	 *
	 * @return  void
	 *
	 * @covers        JPagination::getPagesLinks
	 * @dataProvider  dataTestGetPagesLinks
	 * @since         3.2
	 */
	public function testGetPagesLinks($total, $limitstart, $limit, $expected)
	{
		$pagination = new JPagination($total, $limitstart, $limit);

		$result = $pagination->getPagesLinks();

		$this->assertEquals($result, $expected, 'The expected output of the pagination is incorrect');

		unset($pagination);
	}

	/**
	 * Provides the data to test the getLimitBox method.
	 *
	 * @return  array
	 *
	 * @since   3.1
	 */
	public function dataTestGetLimitBox()
	{
		return array(
			array(100, 0, 20,
				"<select id=\"limit\" name=\"limit\" class=\"inputbox input-mini\" size=\"1\" onchange=\"this.form.submit()\">\n"
				. "\t<option value=\"5\">5</option>\n"
				. "\t<option value=\"10\">10</option>\n"
				. "\t<option value=\"15\">15</option>\n"
				. "\t<option value=\"20\" selected=\"selected\">20</option>\n"
				. "\t<option value=\"25\">25</option>\n"
				. "\t<option value=\"30\">30</option>\n"
				. "\t<option value=\"50\">J50</option>\n"
				. "\t<option value=\"100\">J100</option>\n"
				. "\t<option value=\"0\">JALL</option>\n"
				. "</select>\n"
			),
		);
	}

	/**
	 * This method tests the getLimitBox function.
	 *
	 * @param   integer  $total       The total number of items.
	 * @param   integer  $limitstart  The offset of the item to start at.
	 * @param   integer  $limit       The number of items to display per page.
	 * @param   string   $expected    The expected results for the JPagination object
	 *
	 * @return  void
	 *
	 * @covers        JPagination::getLimitBox
	 * @dataProvider  dataTestGetLimitBox
	 * @since         3.1
	 */
	public function testGetLimitBox($total, $limitstart, $limit, $expected)
	{
		$pagination = new JPagination($total, $limitstart, $limit);

		$this->assertEquals($pagination->getLimitBox(), $expected, 'The limit box results are not as expected');

		unset($pagination);
	}

	/**
	 * Provides the data to test the orderUpIcon method.
	 *
	 * @return  array
	 *
	 * @since   3.2
	 */
	public function dataTestOrderUpIcon()
	{
		return array(
			array(0, '<a class="btn btn-micro  " href="javascript:void(0);" onclick="return listItemTask(\'cb0\',\'orderup\')"><i class="icon-uparrow"></i></a>', true, 'orderup', 'JLIB_HTML_MOVE_UP', true, 'cb'),
			array(2, '<a class="btn btn-micro  " href="javascript:void(0);" onclick="return listItemTask(\'cb2\',\'orderup\')"><i class="icon-uparrow"></i></a>', true, 'orderup', 'JLIB_HTML_MOVE_UP', true, 'cb'),
			array(2, '&#160;', false, 'orderup', 'JLIB_HTML_MOVE_UP', true, 'cb'),
		);
	}

	/**
	 * Test the html string for the orderUpIcon function.
	 *
	 * @param   integer  $i          The row index.
	 * @param   string   $expected   The expected html string
	 * @param   boolean  $condition  True to show the icon.
	 * @param   string   $task       The task to fire.
	 * @param   string   $alt        The image alternative text string.
	 * @param   boolean  $enabled    An optional setting for access control on the action.
	 * @param   string   $checkbox   An optional prefix for checkboxes.
	 *
	 * @return  void
	 *
	 * @covers        JPagination::orderUpIcon
	 * @dataProvider  dataTestOrderUpIcon
	 * @since         3.2
	 */
	public function testOrderUpIcon($i, $expected, $condition = true, $task = 'orderup', $alt = 'JLIB_HTML_MOVE_UP', $enabled = true, $checkbox = 'cb')
	{
		$pagination = new JPagination(100, 50, 20);
		$string = $pagination->orderUpIcon($i, $condition, $task, $alt, $enabled, $checkbox);

		$this->assertEquals($string, $expected, 'This is not the expected order up html');
	}

	/**
	 * Provides the data to test the orderUpIcon method.
	 *
	 * @return  array
	 *
	 * @since   3.2
	 */
	public function dataTestOrderDownIcon()
	{
		return array(
			array(0, 100, '<a class="btn btn-micro  " href="javascript:void(0);" onclick="return listItemTask(\'cb0\',\'orderup\')"><i class="icon-downarrow"></i></a>', true, 'orderup', 'JLIB_HTML_MOVE_DOWN', true, 'cb'),
			array(2, 100, '<a class="btn btn-micro  " href="javascript:void(0);" onclick="return listItemTask(\'cb2\',\'orderup\')"><i class="icon-downarrow"></i></a>', true, 'orderup', 'JLIB_HTML_MOVE_DOWN', true, 'cb'),
			array(2, 100, '&#160;', false, 'orderup', 'JLIB_HTML_MOVE_DOWN', true, 'cb'),
		);
	}

	/**
	 * Test the html string for the orderDownIcon function.
	 *
	 * @param   integer  $i          The row index.
	 * @param   integer  $n          The number of items in the list.
	 * @param   string   $expected   The expected html string
	 * @param   boolean  $condition  True to show the icon.
	 * @param   string   $task       The task to fire.
	 * @param   string   $alt        The image alternative text string.
	 * @param   boolean  $enabled    An optional setting for access control on the action.
	 * @param   string   $checkbox   An optional prefix for checkboxes.
	 *
	 * @return  void
	 *
	 * @covers        JPagination::orderDownIcon
	 * @dataProvider  dataTestOrderDownIcon
	 * @since         3.2
	 */
	public function testOrderDownIcon($i, $n, $expected, $condition = true, $task = 'orderdown', $alt = 'JLIB_HTML_MOVE_DOWN', $enabled = true, $checkbox = 'cb')
	{
		$pagination = new JPagination($n, 50, 20);
		$string = $pagination->orderDownIcon($i, $n, $condition, $task, $alt, $enabled, $checkbox);

		$this->assertEquals($string, $expected, 'This is not the expected order up html');
	}

	/**
	 * Provides the data to test the _list_render method.
	 *
	 * @return  array
	 *
	 * @since   3.2
	 */
	public function dataTestListRender()
	{
		return array(
			array(
				array(
					'start' => array('data' => '<a title="JLIB_HTML_START" href="" class="hasTooltip pagenav">JLIB_HTML_START</a>'),
					'previous' => array('data' => '<a title="JPREV" href="" class="hasTooltip pagenav">JPREV</a>'),
					'next' => array('data' => '<a title="JNEXT" href="" class="hasTooltip pagenav">JNEXT</a>'),
					'end' => array('data' => '<a title="JLIB_HTML_END" href="" class="hasTooltip pagenav">JLIB_HTML_END</a>'),
					'pages' => array(
						'1' => array('data' => '<a href="" class="pagenav">1</a>'),
						'2' => array('data' => '<span class="pagenav">2</span>'),
						'3' => array('data' => '<a href="" class="pagenav">3</a>'),
						'4' => array('data' => '<a href="" class="pagenav">4</a>'),
					),
				), 80, 20, 20,
				'<ul><li class="pagination-start"><a title="JLIB_HTML_START" href="" class="hasTooltip pagenav">JLIB_HTML_START</a></li><li class="pagination-prev"><a title="JPREV" href="" class="hasTooltip pagenav">JPREV</a></li><li><a href="" class="pagenav">1</a></li><li><span class="pagenav">2</span></li><li><a href="" class="pagenav">3</a></li><li><a href="" class="pagenav">4</a></li><li class="pagination-next"><a title="JNEXT" href="" class="hasTooltip pagenav">JNEXT</a></li><li class="pagination-end"><a title="JLIB_HTML_END" href="" class="hasTooltip pagenav">JLIB_HTML_END</a></li></ul>'
			),
		);
	}

	/**
	 * This method tests the _list_render method.
	 *
	 * @param   array    $list        The list array needed for the function _list_render.
	 * @param   integer  $total       The total number of items.
	 * @param   integer  $limitstart  The offset of the item to start at.
	 * @param   integer  $limit       The number of items to display per page.
	 * @param   string   $expected    The expected results for the JPagination object
	 *
	 * @return  void
	 *
	 * @covers        JPagination::_list_render
	 * @dataProvider  dataTestListRender
	 * @since         3.2
	 */
	public function testListRender($list, $total, $limitstart, $limit ,$expected)
	{
		$pagination = new JPagination($total, $limitstart, $limit);

		$string = TestReflection::invoke($pagination, '_list_render', $list);

		$this->assertEquals($string, $expected, 'The list render method is not outputting the expected results');

		unset($pagination);
	}

	/**
	 * Provides the data to test the _item_active method.
	 *
	 * @return  array
	 *
	 * @since   3.2
	 */
	public function dataTestItemActive()
	{
		return array(
			array(
				'JLIB_HTML_START', 100, 40, 20, '<a title="JLIB_HTML_START" href="" class="hasTooltip pagenav">JLIB_HTML_START</a>',
				'JLIB_HTML_VIEW_ALL', 100, 40, 20, '<a title="JLIB_HTML_VIEW_ALL" href="" class="hasTooltip pagenav">JLIB_HTML_VIEW_ALL</a>',
			),
		);
	}

	/**
	 * This method tests the _item_active method.
	 *
	 * @param   string   $text        The link text.
	 * @param   integer  $total       The total number of items.
	 * @param   integer  $limitstart  The offset of the item to start at.
	 * @param   integer  $limit       The number of items to display per page.
	 * @param   string   $expected    The expected results for the JPagination object
	 *
	 * @return  void
	 *
	 * @covers        JPagination::_item_active
	 * @dataProvider  dataTestItemActive
	 * @since         3.2
	 */
	public function testItemActive($text, $total, $limitstart, $limit ,$expected)
	{
		$pagination = new JPagination($total, $limitstart, $limit);
		$paginationObject = new JPaginationObject($text, 0);

		$string = TestReflection::invoke($pagination, '_item_active', $paginationObject);

		$this->assertEquals($string, $expected, 'The list render method is not outputting the expected results');

		unset($pagination);
	}

	/**
	 * Provides the data to test the _item_inactive method.
	 *
	 * @return  array
	 *
	 * @since   3.2
	 */
	public function dataTestItemInactive()
	{
		return array(
			array(
				'3', 100, 40, 20, '<span class="pagenav">3</span>',
			),
		);
	}

	/**
	 * This method tests the _item_active method.
	 *
	 * @param   string   $text        The link text.
	 * @param   integer  $total       The total number of items.
	 * @param   integer  $limitstart  The offset of the item to start at.
	 * @param   integer  $limit       The number of items to display per page.
	 * @param   string   $expected    The expected results for the JPagination object
	 *
	 * @return  void
	 *
	 * @covers        JPagination::_item_inactive
	 * @dataProvider  dataTestItemInactive
	 * @since         3.2
	 */
	public function testItemInactive($text, $total, $limitstart, $limit ,$expected)
	{
		$pagination = new JPagination($total, $limitstart, $limit);
		$paginationObject = new JPaginationObject($text, 0);

		$string = TestReflection::invoke($pagination, '_item_inactive', $paginationObject);

		$this->assertEquals($string, $expected, 'The list render method is not outputting the expected results');

		unset($pagination);
	}

	/**
	 * This method tests the _buildDataObject method.
	 *
	 * @param   integer  $total       The total number of items.
	 * @param   integer  $limitstart  The offset of the item to start at.
	 * @param   integer  $limit       The number of items to display per page.
	 * @param   integer  $active      The page number which contains the active pagination.
	 * @param   array    $expected    The expected results for the JPagination object
	 *
	 * @return  void
	 *
	 * @dataProvider  dataTestBuildDataObject
	 * @covers        JPagination::_buildDataObject
	 * @since         3.2
	 */
	public function testBuildDataObject($total, $limitstart, $limit, $active, $expected)
	{
		$pagination = new JPagination($total, $limitstart, $limit);

		$object = TestReflection::invoke($pagination, '_buildDataObject');

		// Test the view all Object
		$this->assertEquals($object->all->text, $expected["0"]["text"], 'This is not the expected view all text');
		$this->assertEquals($object->all->base, $expected["0"]["base"], 'This is not the expected view all base value');
		$this->assertEquals($object->all->link, $expected["0"]["link"], 'This is not the expected view all link value');
		$this->assertEquals($object->all->prefix, $expected["0"]["prefix"], 'This is not the expected view all prefix value');
		$this->assertEquals($object->all->active, $expected["0"]["active"], 'This is not the expected view all active value');

		// Test the start Object
		$this->assertEquals($object->start->text, $expected["1"]["text"], 'This is not the expected start text');
		$this->assertEquals($object->start->base, $expected["1"]["base"], 'This is not the expected start base value');
		$this->assertEquals($object->start->link, $expected["1"]["link"], 'This is not the expected start link value');
		$this->assertEquals($object->start->prefix, $expected["1"]["prefix"], 'This is not the expected start prefix value');
		$this->assertEquals($object->start->active, $expected["1"]["active"], 'This is not the expected start active value');

		// Test the previous Object
		$this->assertEquals($object->previous->text, $expected["2"]["text"], 'This is not the expected previous text');
		$this->assertEquals($object->previous->base, $expected["2"]["base"], 'This is not the expected previous base value');
		$this->assertEquals($object->previous->link, $expected["2"]["link"], 'This is not the expected previous link value');
		$this->assertEquals($object->previous->prefix, $expected["2"]["prefix"], 'This is not the expected previous prefix value');
		$this->assertEquals($object->previous->active, $expected["2"]["active"], 'This is not the expected previous active value');

		// Test the next Object
		$this->assertEquals($object->next->text, $expected["3"]["text"], 'This is not the expected next text');
		$this->assertEquals($object->next->base, $expected["3"]["base"], 'This is not the expected next base value');
		$this->assertEquals($object->next->link, $expected["3"]["link"], 'This is not the expected next link value');
		$this->assertEquals($object->next->prefix, $expected["3"]["prefix"], 'This is not the expected next prefix value');
		$this->assertEquals($object->next->active, $expected["3"]["active"], 'This is not the expected next active value');

		// Test the end Object
		$this->assertEquals($object->end->text, $expected["4"]["text"], 'This is not the expected end text');
		$this->assertEquals($object->end->base, $expected["4"]["base"], 'This is not the expected end base value');
		$this->assertEquals($object->end->link, $expected["4"]["link"], 'This is not the expected end link value');
		$this->assertEquals($object->end->prefix, $expected["4"]["prefix"], 'This is not the expected end prefix value');
		$this->assertEquals($object->end->active, $expected["4"]["active"], 'This is not the expected end active value');

		// Test the active object
		$this->assertEquals($object->pages[$active]->text, $expected["5"]["text"], 'This is not the expected active text');
		$this->assertEquals($object->pages[$active]->base, $expected["5"]["base"], 'This is not the expected active base value');
		$this->assertEquals($object->pages[$active]->link, $expected["5"]["link"], 'This is not the expected active link value');
		$this->assertEquals($object->pages[$active]->prefix, $expected["5"]["prefix"], 'This is not the expected active prefix value');
		$this->assertEquals($object->pages[$active]->active, $expected["5"]["active"], 'This is not the expected active active value');

		unset($pagination);
	}

	/**
	 * Provides the data to test the set method.
	 *
	 * @return  array
	 *
	 * @since   3.2
	 */
	public function dataSet()
	{
		return array(
			array('limitstart', 40, 40),
			array('viewall', true, true),
			array('pages.start', 30, 30),
		);
	}

	/**
	 * This method tests the set method.
	 *
	 * @param   integer  $property  The property to set
	 * @param   string   $value     The value of the property to set
	 * @param   array    $expected  The expected results for the JPagination object
	 *
	 * @return  void
	 *
	 * @covers        JPagination::set
	 * @dataProvider  dataSet
	 * @since         3.2
	 */
	public function testSet($property, $value, $expected)
	{
		$pagination = new JPagination(100, 50, 20);

		$pagination->set($property, $value);

		if ($property == 'viewall')
		{
			$result = TestReflection::getValue($pagination, $property);
		}
		elseif(strpos($property, '.'))
		{
			$prop = explode('.', $property);
			$prop[1] = ucfirst($prop[1]);
			$property = implode($prop);
			$result = $pagination->$property;
		}
		else
		{
			$result = $pagination->$property;
		}

		$this->assertEquals($result, $expected, 'The expected output of the property is incorrect');

		unset($pagination);
	}

	/**
	 * Provides the data to test the get method.
	 *
	 * @return  array
	 *
	 * @since   3.2
	 */
	public function dataGet()
	{
		return array(
			array('limitstart', '', 50),
			array('viewall', '', false),
			array('falseproperty', null, null),
			array('pages.stop', null, 5),
		);
	}

	/**
	 * This method tests the get method.
	 *
	 * @param   integer  $property  The property to get.
	 * @param   string   $default   The default value if the property doesn't exist
	 * @param   array    $expected  The expected results for the JPagination object
	 *
	 * @return  void
	 *
	 * @covers        JPagination::get
	 * @dataProvider  dataGet
	 * @since         3.2
	 */
	public function testGet($property, $default, $expected)
	{
		$pagination = new JPagination(100, 50, 20);

		$result = $pagination->get($property, $default);
		$this->assertEquals($result, $expected, 'The expected output of the property is incorrect');

		unset($pagination);
	}
}
